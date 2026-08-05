package com.x.bff.config;

import com.sharedlib.logging.HttpLogSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdWebClientCustomizerTest {

    @Test
    void addsCorrelationIdToDownstreamRequest() {
        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(request -> {
                    capturedRequest.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                });
        new CorrelationIdWebClientCustomizer().customize(builder);

        builder.build().get()
                .uri("http://x-store-service/api/v1/stores")
                .exchangeToMono(response -> Mono.empty())
                .contextWrite(context -> context.put(
                        HttpLogSupport.CORRELATION_ID_CONTEXT_KEY,
                        "store-request-456"))
                .block();

        assertThat(capturedRequest.get().headers().getFirst(HttpLogSupport.CORRELATION_ID_HEADER))
                .isEqualTo("store-request-456");
    }
}
