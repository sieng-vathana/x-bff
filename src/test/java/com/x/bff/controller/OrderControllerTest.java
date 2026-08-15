package com.x.bff.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import com.x.bff.service.ServiceClientFactory;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderControllerTest {

    @Test
    void getOrdersForwardsStoreAndPageParameters() {
        CapturedExchange exchange = new CapturedExchange(HttpStatus.OK, """
                {"status":1,"code":200,"data":{"content":[]}}
                """);
        OrderController controller = controller(exchange);

        var response = controller.getOrders(4L, 0, 20).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange.request().method()).isEqualTo(HttpMethod.GET);
        assertThat(exchange.request().url().getPath()).isEqualTo("/api/v1/orders");
        assertThat(exchange.request().url().getQuery())
                .contains("storeId=4", "page=0", "size=20");
    }

    private OrderController controller(CapturedExchange exchange) {
        WebClient orderClient = WebClient.builder()
                .baseUrl("http://x-order-service:8080/api/v1/orders")
                .exchangeFunction(exchange::exchange)
                .build();
        ServiceClientFactory clientFactory = mock(ServiceClientFactory.class);
        when(clientFactory.forService("order", "/api/v1/orders"))
                .thenReturn(orderClient);
        return new OrderController(clientFactory);
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
