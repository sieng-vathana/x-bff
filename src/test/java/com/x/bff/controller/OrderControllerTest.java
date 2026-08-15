package com.x.bff.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.bff.service.ServiceClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderControllerTest {

    @Test
    void getOrdersForwardsStoreAndPageParameters() {
        CapturedExchange orderExchange = new CapturedExchange(HttpStatus.OK, """
                {"status":1,"code":200,"data":{"content":[]}}
                """);
        OrderController controller = controller(orderExchange, new CapturedExchange(HttpStatus.OK, ""));

        var response = controller.getOrders(4L, 0, 20).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(orderExchange.request().method()).isEqualTo(HttpMethod.GET);
        assertThat(orderExchange.request().url().getPath()).isEqualTo("/api/v1/orders");
        assertThat(orderExchange.request().url().getQuery())
                .contains("storeId=4", "page=0", "size=20");
    }

    @Test
    void getOrdersAddsPaymentMethodToEachOrder() {
        CapturedExchange orderExchange = new CapturedExchange(HttpStatus.OK, """
                {"status":1,"code":200,"data":{"content":[{"id":42,"orderNo":"POS-42"}]}}
                """);
        CapturedExchange paymentExchange = new CapturedExchange(HttpStatus.OK, """
                {"status":1,"code":200,"data":[{"method":"CASH","status":"PAID"}]}
                """);
        OrderController controller = controller(orderExchange, paymentExchange);

        var response = controller.getOrders(4L, 0, 20).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(JsonNode.class);
        assertThat(response.getBody().toString()).contains("\"paymentMethod\":\"CASH\"");
        assertThat(paymentExchange.request().url().getQuery()).isEqualTo("orderId=42");
    }

    private OrderController controller(CapturedExchange orderExchange, CapturedExchange paymentExchange) {
        WebClient orderClient = WebClient.builder()
                .baseUrl("http://x-order-service:8080/api/v1/orders")
                .exchangeFunction(orderExchange::exchange)
                .build();
        WebClient paymentClient = WebClient.builder()
                .baseUrl("http://x-payment-service:8080/api/v1/payments")
                .exchangeFunction(paymentExchange::exchange)
                .build();
        ServiceClientFactory clientFactory = mock(ServiceClientFactory.class);
        when(clientFactory.forService("order", "/api/v1/orders"))
                .thenReturn(orderClient);
        when(clientFactory.forService("payment", "/api/v1/payments"))
                .thenReturn(paymentClient);
        return new OrderController(clientFactory, new ObjectMapper());
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
