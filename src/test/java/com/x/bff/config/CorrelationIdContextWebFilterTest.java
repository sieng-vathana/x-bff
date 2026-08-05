package com.x.bff.config;

import com.sharedlib.logging.HttpLogSupport;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdContextWebFilterTest {

    @Test
    void forwardsCorrelationIdInRequestResponseAndReactorContext() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/auth/login")
                .header(HttpLogSupport.CORRELATION_ID_HEADER, "login-request-123")
                .build());
        AtomicReference<String> forwardedHeader = new AtomicReference<>();
        AtomicReference<String> contextValue = new AtomicReference<>();

        new CorrelationIdContextWebFilter().filter(exchange, forwardedExchange -> {
            forwardedHeader.set(forwardedExchange.getRequest().getHeaders()
                    .getFirst(HttpLogSupport.CORRELATION_ID_HEADER));
            return Mono.deferContextual(context -> {
                contextValue.set(context.get(HttpLogSupport.CORRELATION_ID_CONTEXT_KEY));
                return Mono.empty();
            });
        }).block();

        assertThat(forwardedHeader.get()).isEqualTo("login-request-123");
        assertThat(contextValue.get()).isEqualTo("login-request-123");
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpLogSupport.CORRELATION_ID_HEADER))
                .isEqualTo("login-request-123");
    }
}
