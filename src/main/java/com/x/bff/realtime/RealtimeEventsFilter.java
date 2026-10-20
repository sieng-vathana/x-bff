package com.x.bff.realtime;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * After a request that changes what the backoffice approval queue or a merchant's status shows succeeds, tells the
 * screens showing them so they reload, instead of each one asking every few seconds.
 */
@Component
public class RealtimeEventsFilter implements WebFilter {

    private static final Pattern REGISTER = Pattern.compile("/api/v1/auth/register");
    private static final Pattern MERCHANT_REVIEW = Pattern.compile("/api/v1/businesses/(\\d+)/approval/(approve|reject|resubmit)");
    private static final Pattern MARKETPLACE_REVIEW = Pattern.compile("/api/v1/stores/\\d+/marketplace/(apply|approve|reject|request-changes)");
    private static final Pattern MERCHANT_CONTROL = Pattern.compile("/api/v1/admin/merchants/(\\d+)/(suspension|plan)");

    private final RealtimePublisher realtime;

    public RealtimeEventsFilter(RealtimePublisher realtime) {
        this.realtime = realtime;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        HttpMethod method = exchange.getRequest().getMethod();
        if (HttpMethod.GET.equals(method) || HttpMethod.OPTIONS.equals(method) || HttpMethod.HEAD.equals(method)) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            if (status == null || status.is2xxSuccessful()) publish(path);
        }));
    }

    void publish(String path) {
        if (REGISTER.matcher(path).matches() || MARKETPLACE_REVIEW.matcher(path).matches()) {
            realtime.toAdmins("approvals.changed", Map.of());
            return;
        }
        Matcher review = MERCHANT_REVIEW.matcher(path);
        if (review.matches()) {
            realtime.toAdmins("approvals.changed", Map.of());
            realtime.toBusiness(Long.valueOf(review.group(1)), "business.updated", Map.of());
            return;
        }
        Matcher control = MERCHANT_CONTROL.matcher(path);
        if (control.matches()) {
            realtime.toBusiness(Long.valueOf(control.group(1)), "business.updated", Map.of());
        }
    }
}
