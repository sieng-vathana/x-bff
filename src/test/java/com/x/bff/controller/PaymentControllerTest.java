package com.x.bff.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.bff.service.ServiceClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentControllerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void createQrForwardsToPaymentService() throws Exception {
        CapturedExchange exchange = new CapturedExchange(HttpStatus.CREATED, """
                {"status":1,"code":201,"data":{"transactionId":"XP-42-test"}}
                """);
        PaymentController controller = controller(exchange);

        var response = controller.createQr(OBJECT_MAPPER.readTree("""
                {"orderId":42,"amount":1.00,"currencyCode":"USD"}
                """)).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(exchange.request().method()).isEqualTo(HttpMethod.POST);
        assertThat(exchange.request().url().getPath()).isEqualTo("/api/v1/payments/qr");
    }

    @Test
    void getPaymentForwardsToPaymentService() {
        CapturedExchange exchange = new CapturedExchange(HttpStatus.OK, """
                {"status":1,"code":200,"data":{"id":9,"status":"PENDING"}}
                """);
        PaymentController controller = controller(exchange);

        var response = controller.get(9L).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange.request().method()).isEqualTo(HttpMethod.GET);
        assertThat(exchange.request().url().getPath()).isEqualTo("/api/v1/payments/9");
    }

    @Test
    void paymentTestRoutesRequireCreatePermission() throws Exception {
        Method createQr = PaymentController.class.getMethod("createQr", com.fasterxml.jackson.databind.JsonNode.class);
        Method get = PaymentController.class.getMethod("get", Long.class);

        assertThat(createQr.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('x-payment:create')");
        assertThat(get.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('x-payment:read') or hasAuthority('x-payment:create')");
    }

    private PaymentController controller(CapturedExchange exchange) {
        WebClient paymentClient = WebClient.builder()
                .baseUrl("http://payment-service:8085/api/v1/payments")
                .exchangeFunction(exchange::exchange)
                .build();
        ServiceClientFactory clientFactory = mock(ServiceClientFactory.class);
        when(clientFactory.forService("payment", "/api/v1/payments"))
                .thenReturn(paymentClient);
        return new PaymentController(clientFactory);
    }

    private static final class CapturedExchange {
        private final AtomicReference<ClientRequest> request = new AtomicReference<>();
        private final HttpStatus status;
        private final String body;

        private CapturedExchange(HttpStatus status, String body) {
            this.status = status;
            this.body = body;
        }

        private Mono<ClientResponse> exchange(ClientRequest clientRequest) {
            request.set(clientRequest);
            return Mono.just(ClientResponse.create(status)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build());
        }

        private ClientRequest request() {
            return request.get();
        }
    }
}
