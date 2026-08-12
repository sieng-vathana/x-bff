package com.x.bff.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.bff.dto.PosOrderItemRequest;
import com.x.bff.dto.PosQrCheckoutRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PosQrCheckoutServiceTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final byte[] PNG = "test-png".getBytes(StandardCharsets.UTF_8);

    @Test
    void marksProductionConstructorForSpringInjection() {
        Constructor<?> productionConstructor = PosQrCheckoutService.class.getConstructors()[0];

        assertThat(productionConstructor.isAnnotationPresent(Autowired.class)).isTrue();
    }

    @Test
    void createsOrderAndPaymentAndEmbedsQrInOneResponse() {
        CapturedJsonExchange order = new CapturedJsonExchange("""
                {"status":1,"code":201,"data":{"id":42,"orderNo":"ORD-42","grandTotal":1.00,"currencyCode":"USD"}}
                """);
        CapturedJsonExchange payment = new CapturedJsonExchange("""
                {"status":1,"code":201,"data":{"transactionId":"XP-42","qrPayload":"payload","qrImageUrl":"https://khqr.cc/api/khqr/XP-42"}}
                """);
        AtomicReference<ClientRequest> imageRequest = new AtomicReference<>();
        WebClient imageClient = WebClient.builder().exchangeFunction(request -> {
            imageRequest.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.IMAGE_PNG_VALUE)
                    .body(Flux.just(new DefaultDataBufferFactory().wrap(PNG)))
                    .build());
        }).build();
        PosQrCheckoutService service = new PosQrCheckoutService(
                client(order), client(payment), imageClient, OBJECT_MAPPER);

        StepVerifier.create(service.create(request()))
                .assertNext(result -> {
                    assertThat(result.order().path("orderNo").asText()).isEqualTo("ORD-42");
                    assertThat(result.payment().path("transactionId").asText()).isEqualTo("XP-42");
                    assertThat(result.payment().path("qrImageDataUrl").asText())
                            .isEqualTo("data:image/png;base64,dGVzdC1wbmc=");
                })
                .verifyComplete();

        assertThat(order.request().method()).isEqualTo(HttpMethod.POST);
        assertThat(order.request().url().getPath()).isEqualTo("/api/v1/orders/pos");
        assertThat(payment.request().method()).isEqualTo(HttpMethod.POST);
        assertThat(payment.request().url().getPath()).isEqualTo("/api/v1/payments/qr");
        assertThat(imageRequest.get().url().toString()).isEqualTo("https://khqr.cc/api/khqr/XP-42");
    }

    private PosQrCheckoutRequest request() {
        return new PosQrCheckoutRequest(
                2L,
                2L,
                0L,
                6L,
                "USD",
                BigDecimal.ZERO,
                "POS-1",
                "PAY-1",
                "Item x1",
                List.of(new PosOrderItemRequest(2L, 1, null, null, null)));
    }

    private WebClient client(CapturedJsonExchange exchange) {
        return WebClient.builder()
                .baseUrl(exchange == null ? "http://service" : exchange.baseUrl())
                .exchangeFunction(exchange::exchange)
                .build();
    }

    private static final class CapturedJsonExchange {
        private final AtomicReference<ClientRequest> request = new AtomicReference<>();
        private final String body;
        private String baseUrl;

        private CapturedJsonExchange(String body) {
            this.body = body;
        }

        private String baseUrl() {
            if (baseUrl == null) {
                baseUrl = body.contains("orderNo")
                        ? "http://order-service/api/v1/orders"
                        : "http://payment-service/api/v1/payments";
            }
            return baseUrl;
        }

        private Mono<ClientResponse> exchange(ClientRequest clientRequest) {
            request.set(clientRequest);
            return Mono.just(ClientResponse.create(HttpStatus.CREATED)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build());
        }

        private ClientRequest request() {
            return request.get();
        }
    }
}
