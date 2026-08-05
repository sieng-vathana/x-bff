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

class ProductControllerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void getUnitsForwardsQueryParametersAndResponse() {
        CapturedExchange exchange = new CapturedExchange(HttpStatus.OK, """
                {"status":1,"code":200,"data":{"content":[]}}
                """);
        ProductController controller = controller(exchange);

        var response = controller.getUnits(1L, "2", 0, 20).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange.request().method()).isEqualTo(HttpMethod.GET);
        assertThat(exchange.request().url().getPath()).isEqualTo("/api/v1/products/units");
        assertThat(exchange.request().url().getQuery())
                .contains("businessId=1", "storeId=2", "page=0", "size=20");
    }

    @Test
    void createCategoryForwardsPostAndCreatedStatus() throws Exception {
        CapturedExchange exchange = new CapturedExchange(HttpStatus.CREATED, """
                {"status":1,"code":201,"data":{"id":9}}
                """);
        ProductController controller = controller(exchange);

        var response = controller.createCategory(
                        OBJECT_MAPPER.readTree("{\"businessId\":1,\"categoryCode\":\"DRY\"}"))
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(exchange.request().method()).isEqualTo(HttpMethod.POST);
        assertThat(exchange.request().url().getPath()).isEqualTo("/api/v1/products/categories");
        assertThat(exchange.request().headers().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void deleteUnitForwardsBusinessIdAndSuccessfulResponse() {
        CapturedExchange exchange = new CapturedExchange(HttpStatus.OK, """
                {"status":1,"code":200,"message":"Product unit deleted"}
                """);
        ProductController controller = controller(exchange);

        var response = controller.deleteUnit(7L, 1L).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange.request().method()).isEqualTo(HttpMethod.DELETE);
        assertThat(exchange.request().url().getPath()).isEqualTo("/api/v1/products/units/7");
        assertThat(exchange.request().url().getQuery()).isEqualTo("businessId=1");
    }

    @Test
    void downstreamValidationStatusIsPreserved() {
        CapturedExchange exchange = new CapturedExchange(HttpStatus.BAD_REQUEST, """
                {"status":-1,"code":400,"message":"businessId must be positive"}
                """);
        ProductController controller = controller(exchange);

        var response = controller.getCategory(9L, 0L).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void referenceRoutesRequireTheirDedicatedPermissions() throws Exception {
        Method unitMethod = ProductController.class.getMethod(
                "getUnits", Long.class, String.class, int.class, int.class);
        Method categoryMethod = ProductController.class.getMethod(
                "getCategories", Long.class, String.class, int.class, int.class);

        assertThat(unitMethod.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('x-product:unit') or hasAuthority('x-product:read')");
        assertThat(categoryMethod.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('x-product:category') or hasAuthority('x-product:read')");
    }

    private ProductController controller(CapturedExchange exchange) {
        WebClient productClient = WebClient.builder()
                .baseUrl("http://product-service:8082/api/v1/products")
                .exchangeFunction(exchange::exchange)
                .build();
        ServiceClientFactory clientFactory = mock(ServiceClientFactory.class);
        when(clientFactory.forService("product", "/api/v1/products"))
                .thenReturn(productClient);
        return new ProductController(clientFactory);
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
