package com.x.bff.config;

import com.sharedlib.logging.HttpLogSupport;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Propagates the current request correlation ID to every auto-configured WebClient. */
@Component
public class CorrelationIdWebClientCustomizer implements WebClientCustomizer {

    @Override
    public void customize(WebClient.Builder webClientBuilder) {
        webClientBuilder.filter((request, next) -> Mono.deferContextual(context -> {
            String correlationId = context.getOrDefault(
                    HttpLogSupport.CORRELATION_ID_CONTEXT_KEY,
                    "-");
            if ("-".equals(HttpLogSupport.sanitizeCorrelationId(correlationId))) {
                return next.exchange(request);
            }
            ClientRequest correlatedRequest = ClientRequest.from(request)
                    .headers(headers -> headers.set(
                            HttpLogSupport.CORRELATION_ID_HEADER,
                            correlationId))
                    .build();
            return next.exchange(correlatedRequest);
        }));
    }
}
