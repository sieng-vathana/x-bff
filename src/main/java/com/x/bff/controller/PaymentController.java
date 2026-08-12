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

    @PostMapping("/khqrpay/webhook")
    public Mono<ResponseEntity<?>> khqrPayWebhook(@RequestBody JsonNode request) {
        return forward(paymentClient.post()
                .uri("/khqrpay/webhook")
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
