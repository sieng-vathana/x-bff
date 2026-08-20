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

    @Test
    void getHeldOrdersForwardsStoreAndPageParametersWithoutPaymentLookups() {
        CapturedExchange orderExchange = new CapturedExchange(HttpStatus.OK, """
                {"status":1,"code":200,"data":{"content":[]}}
                """);
        CapturedExchange paymentExchange = new CapturedExchange(HttpStatus.OK, "");
        OrderController controller = controller(orderExchange, paymentExchange);

        var response = controller.getHeldOrders(4L, 0, 50).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(orderExchange.request().method()).isEqualTo(HttpMethod.GET);
        assertThat(orderExchange.request().url().getPath()).isEqualTo("/api/v1/orders/holds");
        assertThat(orderExchange.request().url().getQuery())
                .contains("storeId=4", "page=0", "size=50");
        assertThat(paymentExchange.request()).isNull();
    }

    @Test
    void salesSummaryForwardsReportParameters() {
        CapturedExchange orderExchange = new CapturedExchange(HttpStatus.OK, """
                {"status":1,"code":200,"data":{"orderCount":2}}
                """);
        OrderController controller = controller(orderExchange, new CapturedExchange(HttpStatus.OK, ""));

        var response = controller.salesSummary(4L, "2026-08-15T00:00:00", "2026-08-22T00:00:00").block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(orderExchange.request().url().getPath()).isEqualTo("/api/v1/orders/reports/sales-summary");
        assertThat(orderExchange.request().url().getQuery())
                .contains("storeId=4", "from=2026-08-15T00:00:00", "to=2026-08-22T00:00:00");
    }

    @Test
    void topProductsForwardsReportParametersAndLimit() {
        CapturedExchange orderExchange = new CapturedExchange(HttpStatus.OK, """
                {"status":1,"code":200,"data":[]}
                """);
        OrderController controller = controller(orderExchange, new CapturedExchange(HttpStatus.OK, ""));

        controller.topProducts(4L, "2026-08-15T00:00:00", "2026-08-22T00:00:00", 5).block();

        assertThat(orderExchange.request().url().getPath()).isEqualTo("/api/v1/orders/reports/top-products");
        assertThat(orderExchange.request().url().getQuery())
                .contains("storeId=4", "limit=5", "from=2026-08-15T00:00:00", "to=2026-08-22T00:00:00");
    }

    @Test
    void getOrderForwardsOrderId() {
        CapturedExchange orderExchange = new CapturedExchange(HttpStatus.OK, """
                {"status":1,"code":200,"data":{"id":42}}
                """);
        OrderController controller = controller(orderExchange, new CapturedExchange(HttpStatus.OK, ""));

        controller.get(42L).block();

        assertThat(orderExchange.request().method()).isEqualTo(HttpMethod.GET);
        assertThat(orderExchange.request().url().getPath()).isEqualTo("/api/v1/orders/42");
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
