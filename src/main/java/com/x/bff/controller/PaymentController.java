package com.x.bff.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sharedlib.response.ApiResponse;
import com.x.bff.dto.PosQrCheckoutRequest;
import com.x.bff.dto.PosQrCheckoutResponse;
import com.x.bff.service.PosQrCheckoutService;
import com.x.bff.service.ServiceClientFactory;
import com.x.bff.utils.XUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final WebClient paymentClient;
    private final PosQrCheckoutService posQrCheckoutService;

    public PaymentController(ServiceClientFactory clientFactory, PosQrCheckoutService posQrCheckoutService) {
        this.paymentClient = clientFactory.forService("payment", "/api/v1/payments");
        this.posQrCheckoutService = posQrCheckoutService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('x-payment:create')")
    public Mono<ResponseEntity<?>> create(@RequestBody JsonNode request) {
        return forward(paymentClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request));
    }

    @PostMapping("/qr")
    @PreAuthorize("hasAuthority('x-payment:create')")
    public Mono<ResponseEntity<?>> createQr(@RequestBody JsonNode request) {
        return forward(paymentClient.post()
                .uri("/qr")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request));
    }

    @PostMapping("/pos-qr")
    @PreAuthorize("hasAuthority('x-order:create') and hasAuthority('x-payment:create')")
    public Mono<ResponseEntity<ApiResponse<PosQrCheckoutResponse>>> createPosQr(
            @Valid @RequestBody PosQrCheckoutRequest request) {
        return posQrCheckoutService.create(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(
                                HttpStatus.CREATED.value(),
                                "POS QR checkout created",
                                response)));
    }

    @PostMapping("/pos-qr-demo")
    @PreAuthorize("hasAuthority('x-order:create') and hasAuthority('x-payment:create')")
    public Mono<ResponseEntity<ApiResponse<PosQrCheckoutResponse>>> createSimulatedPosQr(
            @Valid @RequestBody PosQrCheckoutRequest request) {
        return posQrCheckoutService.createSimulated(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(
                                HttpStatus.CREATED.value(),
                                "Simulated POS QR checkout created",
                                response)));
    }

    @PostMapping("/khqrpay/webhook")
    public Mono<ResponseEntity<?>> khqrPayWebhook(@RequestBody JsonNode request) {
        return forward(paymentClient.post()
                .uri("/khqrpay/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request));
    }

    @PostMapping("/{id}/simulate-callback")
    @PreAuthorize("hasAuthority('x-payment:create')")
    public Mono<ResponseEntity<?>> simulateCallback(@PathVariable Long id) {
        return forward(paymentClient.post().uri("/{id}/simulate-callback", id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('x-payment:read') or hasAuthority('x-payment:create') or hasAuthority('x-order:refund')")
    public Mono<ResponseEntity<?>> listForOrder(@RequestParam Long orderId) {
        return forward(paymentClient.get().uri(uri -> uri.queryParam("orderId", orderId).build()));
    }

    @GetMapping("/reports/breakdown")
    @PreAuthorize("hasAuthority('x-report:read')")
    public Mono<ResponseEntity<?>> breakdown(
            @RequestParam Long storeId, @RequestParam String from, @RequestParam String to) {
        return forward(paymentClient.get()
                .uri(uri -> uri.path("/reports/breakdown")
                        .queryParam("storeId", storeId)
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .build()));
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAuthority('x-payment:refund') or hasAuthority('x-order:refund')")
    public Mono<ResponseEntity<?>> refund(@PathVariable Long id, @RequestBody JsonNode request) {
        return forward(paymentClient.post()
                .uri("/{id}/refund", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request));
    }

    @GetMapping("/cash-sessions/current")
    @PreAuthorize("hasAuthority('x-payment:read') or hasAuthority('x-payment:create')")
    public Mono<ResponseEntity<?>> currentCashSession(
            @RequestParam Long storeId, @RequestParam Long cashierId, @RequestParam String currencyCode) {
        return forward(paymentClient.get().uri(uri -> uri.path("/cash-sessions/current")
                .queryParam("storeId", storeId)
                .queryParam("cashierId", cashierId)
                .queryParam("currencyCode", currencyCode)
                .build()));
    }

    @GetMapping("/cash-sessions/history")
    @PreAuthorize("hasAuthority('x-payment:read') or hasAuthority('x-payment:create')")
    public Mono<ResponseEntity<?>> cashSessionHistory(
            @RequestParam Long storeId, @RequestParam Long cashierId, @RequestParam String currencyCode) {
        return forward(paymentClient.get().uri(uri -> uri.path("/cash-sessions/history")
                .queryParam("storeId", storeId)
                .queryParam("cashierId", cashierId)
                .queryParam("currencyCode", currencyCode)
                .build()));
    }

    @PostMapping("/cash-sessions/open")
    @PreAuthorize("hasAuthority('x-payment:create')")
    public Mono<ResponseEntity<?>> openCashSession(@RequestBody JsonNode request) {
        return forward(paymentClient.post()
                .uri("/cash-sessions/open")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request));
    }

    @GetMapping("/cash-sessions/{id}")
    @PreAuthorize("hasAuthority('x-payment:read') or hasAuthority('x-payment:create')")
    public Mono<ResponseEntity<?>> getCashSession(@PathVariable Long id) {
        return forward(paymentClient.get().uri("/cash-sessions/{id}", id));
    }

    @PostMapping("/cash-sessions/{id}/movements")
    @PreAuthorize("hasAuthority('x-payment:create')")
    public Mono<ResponseEntity<?>> addCashMovement(@PathVariable Long id, @RequestBody JsonNode request) {
        return forward(paymentClient.post()
                .uri("/cash-sessions/{id}/movements", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request));
    }

    @PostMapping("/cash-sessions/{id}/close")
    @PreAuthorize("hasAuthority('x-payment:create')")
    public Mono<ResponseEntity<?>> closeCashSession(@PathVariable Long id, @RequestBody JsonNode request) {
        return forward(paymentClient.post()
                .uri("/cash-sessions/{id}/close", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('x-payment:read') or hasAuthority('x-payment:create')")
    public Mono<ResponseEntity<?>> get(@PathVariable Long id) {
        return forward(paymentClient.get().uri("/{id}", id));
    }

    private Mono<ResponseEntity<?>> forward(WebClient.RequestHeadersSpec<?> request) {
        return request.exchangeToMono(response -> response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> XUtil.toJsonResponse(body, response.statusCode())));
    }
}
