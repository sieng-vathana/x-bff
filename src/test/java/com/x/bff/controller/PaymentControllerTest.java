package com.x.bff.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.bff.dto.PosOrderItemRequest;
import com.x.bff.dto.PosQrCheckoutRequest;
import com.x.bff.dto.PosQrCheckoutResponse;
import com.x.bff.service.PosQrCheckoutService;
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
import java.math.BigDecimal;
import java.util.List;
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
    void paymentTestRoutesRequireExpectedPermission() throws Exception {
        Method createQr = PaymentController.class.getMethod("createQr", com.fasterxml.jackson.databind.JsonNode.class);
        Method get = PaymentController.class.getMethod("get", Long.class);
        Method list = PaymentController.class.getMethod("listForOrder", Long.class);
        Method refund = PaymentController.class.getMethod("refund", Long.class, com.fasterxml.jackson.databind.JsonNode.class);
        Method createPosQr = PaymentController.class.getMethod("createPosQr", PosQrCheckoutRequest.class);

        assertThat(createQr.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('x-payment:create')");
        assertThat(get.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('x-payment:read') or hasAuthority('x-payment:create')");
        assertThat(list.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('x-payment:read') or hasAuthority('x-payment:create') or hasAuthority('x-order:refund')");
        assertThat(refund.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('x-payment:refund') or hasAuthority('x-order:refund')");
        assertThat(createPosQr.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('x-order:create') and hasAuthority('x-payment:create')");
    }

    @Test
    void createPosQrReturnsOneCombinedResponse() throws Exception {
        CapturedExchange exchange = new CapturedExchange(HttpStatus.CREATED, "{}");
        PosQrCheckoutService checkoutService = mock(PosQrCheckoutService.class);
        PosQrCheckoutRequest request = new PosQrCheckoutRequest(
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
        PosQrCheckoutResponse checkout = new PosQrCheckoutResponse(
                OBJECT_MAPPER.readTree("{\"id\":42,\"orderNo\":\"ORD-42\"}"),
                OBJECT_MAPPER.readTree("{\"transactionId\":\"XP-42\",\"qrImageDataUrl\":\"data:image/png;base64,UE5H\"}"));
        when(checkoutService.create(request)).thenReturn(Mono.just(checkout));
        PaymentController controller = controller(exchange, checkoutService);

        var response = controller.createPosQr(request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEqualTo(checkout);
    }

    @Test
    void simulateCallbackForwardsToPaymentService() {
        CapturedExchange exchange = new CapturedExchange(HttpStatus.OK, """
                {"status":1,"code":200,"data":{"id":9,"status":"PAID","provider":"SIMULATED"}}
                """);
        PaymentController controller = controller(exchange);

        var response = controller.simulateCallback(9L).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange.request().method()).isEqualTo(HttpMethod.POST);
        assertThat(exchange.request().url().getPath()).isEqualTo("/api/v1/payments/9/simulate-callback");
    }

    @Test
    void listForOrderForwardsOrderId() {
        CapturedExchange exchange = new CapturedExchange(HttpStatus.OK, """
                {"status":1,"code":200,"data":[]}
                """);
        PaymentController controller = controller(exchange);

        controller.listForOrder(42L).block();

        assertThat(exchange.request().method()).isEqualTo(HttpMethod.GET);
        assertThat(exchange.request().url().getPath()).isEqualTo("/api/v1/payments");
        assertThat(exchange.request().url().getQuery()).isEqualTo("orderId=42");
    }

    @Test
    void breakdownForwardsReportParameters() {
        CapturedExchange exchange = new CapturedExchange(HttpStatus.OK, """
                {"status":1,"code":200,"data":[]}
                """);
        PaymentController controller = controller(exchange);

        controller.breakdown(4L, "2026-08-15T00:00:00", "2026-08-22T00:00:00").block();

        assertThat(exchange.request().url().getPath()).isEqualTo("/api/v1/payments/reports/breakdown");
        assertThat(exchange.request().url().getQuery())
                .contains("storeId=4", "from=2026-08-15T00:00:00", "to=2026-08-22T00:00:00");
    }

    @Test
    void cashSessionEndpointsForwardToPaymentService() throws Exception {
        CapturedExchange exchange = new CapturedExchange(HttpStatus.OK, """
                {"status":1,"code":200,"data":{}}
                """);
        PaymentController controller = controller(exchange);

        controller.currentCashSession(4L, 7L, "USD").block();
        assertThat(exchange.request().method()).isEqualTo(HttpMethod.GET);
        assertThat(exchange.request().url().getPath()).isEqualTo("/api/v1/payments/cash-sessions/current");
        assertThat(exchange.request().url().getQuery()).contains("storeId=4", "cashierId=7", "currencyCode=USD");

        controller.openCashSession(OBJECT_MAPPER.readTree("{\"storeId\":4,\"cashierId\":7}")).block();
        assertThat(exchange.request().method()).isEqualTo(HttpMethod.POST);
        assertThat(exchange.request().url().getPath()).isEqualTo("/api/v1/payments/cash-sessions/open");

        controller.addCashMovement(11L, OBJECT_MAPPER.readTree("{\"amount\":2}")).block();
        assertThat(exchange.request().url().getPath()).isEqualTo("/api/v1/payments/cash-sessions/11/movements");

        controller.closeCashSession(11L, OBJECT_MAPPER.readTree("{\"countedCash\":2}")).block();
        assertThat(exchange.request().url().getPath()).isEqualTo("/api/v1/payments/cash-sessions/11/close");
    }

    @Test
    void cashSessionRoutesUsePaymentPermissions() throws Exception {
        Method current = PaymentController.class.getMethod("currentCashSession", Long.class, Long.class, String.class);
        Method open = PaymentController.class.getMethod("openCashSession", com.fasterxml.jackson.databind.JsonNode.class);
        Method movement = PaymentController.class.getMethod("addCashMovement", Long.class, com.fasterxml.jackson.databind.JsonNode.class);
        Method close = PaymentController.class.getMethod("closeCashSession", Long.class, com.fasterxml.jackson.databind.JsonNode.class);

        assertThat(current.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('x-payment:read') or hasAuthority('x-payment:create')");
        assertThat(open.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAuthority('x-payment:create')");
        assertThat(movement.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAuthority('x-payment:create')");
        assertThat(close.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAuthority('x-payment:create')");
    }

    @Test
    void refundForwardsPaymentId() throws Exception {
        CapturedExchange exchange = new CapturedExchange(HttpStatus.OK, """
                {"status":1,"code":200,"data":{"id":9,"status":"REFUNDED"}}
                """);
        PaymentController controller = controller(exchange);

        controller.refund(9L, OBJECT_MAPPER.readTree("{\"amount\":1,\"reason\":\"test\"}")).block();

        assertThat(exchange.request().method()).isEqualTo(HttpMethod.POST);
        assertThat(exchange.request().url().getPath()).isEqualTo("/api/v1/payments/9/refund");
    }

    private PaymentController controller(CapturedExchange exchange) {
        return controller(exchange, mock(PosQrCheckoutService.class));
    }

    private PaymentController controller(CapturedExchange exchange, PosQrCheckoutService checkoutService) {
        WebClient paymentClient = WebClient.builder()
                .baseUrl("http://payment-service:8085/api/v1/payments")
                .exchangeFunction(exchange::exchange)
                .build();
        ServiceClientFactory clientFactory = mock(ServiceClientFactory.class);
        when(clientFactory.forService("payment", "/api/v1/payments"))
                .thenReturn(paymentClient);
        return new PaymentController(clientFactory, checkoutService);
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
