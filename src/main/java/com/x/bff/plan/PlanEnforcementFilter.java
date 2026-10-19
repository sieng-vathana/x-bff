package com.x.bff.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.bff.security.JwtUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Keeps each merchant inside its plan: features outside the plan answer 403 PLAN_REQUIRED, and creating a store,
 * staff member or product beyond the plan's limit answers 403 PLAN_LIMIT. The merchant comes from the access
 * token's "bid" claim; requests without one (platform admins, sessions from before plans) are not checked.
 */
@Component
public class PlanEnforcementFilter implements WebFilter {

    private record Rule(Pattern path, HttpMethod method, String feature, String featureLabel) {
        boolean matches(HttpMethod requestMethod, String requestPath) {
            return (method == null || method.equals(requestMethod)) && path.matcher(requestPath).matches();
        }
    }

    private record Limit(Pattern path, String field, String noun, String nounPlural, Function<Long, Mono<Long>> counter) {
    }

    private static Rule rule(String regex, HttpMethod method, String feature, String label) {
        return new Rule(Pattern.compile(regex), method, feature, label);
    }

    private final List<Rule> rules = List.of(
            rule("/api/v1/purchases(/.*)?", null, "PURCHASING", "Purchasing"),
            rule("/api/v1/stock/(movements|reports/low-stock|expiry|transfers)(/.*)?", null, "INVENTORY", "Inventory"),
            rule("/api/v1/payments/cash-sessions/history", HttpMethod.GET, "SHIFT_REPORTS", "Shift history"),
            rule("/api/v1/customers(/.*)?", null, "CUSTOMERS_LOYALTY", "Customers and loyalty"),
            rule("/api/v1/loyalty(/.*)?", null, "CUSTOMERS_LOYALTY", "Customers and loyalty"),
            rule("/api/v1/roles(/[0-9]+)?", HttpMethod.POST, "CUSTOM_ROLES", "Custom roles"),
            rule("/api/v1/roles/[0-9]+", HttpMethod.PUT, "CUSTOM_ROLES", "Custom roles"),
            rule("/api/v1/roles/[0-9]+", HttpMethod.DELETE, "CUSTOM_ROLES", "Custom roles"),
            rule("/api/v1/stores/[0-9]+/marketplace/apply", HttpMethod.POST, "MARKETPLACE", "The TinhLuk marketplace"),
            rule("/api/v1/deliveries(/.*)?", null, "DELIVERY", "Delivery booking"));

    private final List<Limit> limits;
    private final PlanService planService;
    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PlanEnforcementFilter(PlanService planService, JwtUtils jwtUtils) {
        this.planService = planService;
        this.jwtUtils = jwtUtils;
        this.limits = List.of(
                new Limit(Pattern.compile("/api/v1/stores/?"), "maxStores", "store", "stores", planService::countStores),
                new Limit(Pattern.compile("/api/v1/users/?"), "maxStaff", "staff account", "staff accounts", planService::countStaff),
                new Limit(Pattern.compile("/api/v1/products/?"), "maxProducts", "product", "products", planService::countProducts));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();
        Rule rule = rules.stream().filter(candidate -> candidate.matches(method, path)).findFirst().orElse(null);
        Limit limit = HttpMethod.POST.equals(method)
                ? limits.stream().filter(candidate -> candidate.path().matcher(path).matches()).findFirst().orElse(null)
                : null;
        if (rule == null && limit == null) return chain.filter(exchange);

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getCredentials)
                .ofType(String.class)
                .mapNotNull(jwtUtils::extractBusinessId)
                .flatMap(businessId -> planService.planOf(businessId)
                        .flatMap(plan -> check(exchange, rule, limit, businessId, plan)))
                .switchIfEmpty(Mono.just(Boolean.TRUE))
                .onErrorResume(error -> Mono.just(Boolean.TRUE))
                .flatMap(allowed -> Boolean.TRUE.equals(allowed) ? chain.filter(exchange) : Mono.empty());
    }

    /** True to continue; false once the refusal has been written. */
    private Mono<Boolean> check(ServerWebExchange exchange, Rule rule, Limit limit, Long businessId, JsonNode plan) {
        String planName = plan.path("name").asText("current");
        if (rule != null && !PlanService.includes(plan, rule.feature())) {
            String needed = "MARKETPLACE".equals(rule.feature()) || "DELIVERY".equals(rule.feature()) ? "Pro" : "Business";
            return refuse(exchange, "PLAN_REQUIRED", rule.featureLabel() + " is not in your " + planName + " plan. It comes with the "
                    + needed + " plan. Ask TinhLuk to upgrade your plan.", Map.of("feature", rule.feature(), "requiredPlan", needed.toUpperCase()));
        }
        if (limit == null) return Mono.just(true);
        Integer max = PlanService.limit(plan, limit.field());
        if (max == null) return Mono.just(true);
        return limit.counter().apply(businessId).flatMap(used -> used < max ? Mono.just(true)
                : refuse(exchange, "PLAN_LIMIT", "Your " + planName + " plan allows " + max + " " + (max == 1 ? limit.noun() : limit.nounPlural())
                        + " and you have " + used + ". Ask TinhLuk to upgrade your plan to add more.",
                        Map.of("limit", max, "used", used)));
    }

    private Mono<Boolean> refuse(ServerWebExchange exchange, String error, String message, Map<String, Object> details) {
        Map<String, Object> data = new LinkedHashMap<>(details);
        data.put("error", error);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 0);
        body.put("code", 403);
        body.put("message", message);
        body.put("data", data);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception exception) {
            bytes = ("{\"status\":0,\"code\":403,\"message\":\"Not in your plan\"}").getBytes();
        }
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer)).thenReturn(false);
    }
}
