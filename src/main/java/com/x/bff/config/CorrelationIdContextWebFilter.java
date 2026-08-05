package com.x.bff.config;

import com.sharedlib.logging.HttpLogSupport;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Makes the gateway correlation ID available to downstream WebClient calls. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CorrelationIdContextWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestedCorrelationId = HttpLogSupport.sanitizeCorrelationId(
                exchange.getRequest().getHeaders().getFirst(HttpLogSupport.CORRELATION_ID_HEADER));
        String correlationId = "-".equals(requestedCorrelationId)
                ? UUID.randomUUID().toString()
                : requestedCorrelationId;

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(HttpLogSupport.CORRELATION_ID_HEADER, correlationId))
                .build();
        exchange.getResponse().getHeaders().set(HttpLogSupport.CORRELATION_ID_HEADER, correlationId);

        String contextCorrelationId = correlationId;
        return chain.filter(exchange.mutate().request(request).build())
                .contextWrite(context -> context.put(
                        HttpLogSupport.CORRELATION_ID_CONTEXT_KEY,
                        contextCorrelationId));
    }
}
