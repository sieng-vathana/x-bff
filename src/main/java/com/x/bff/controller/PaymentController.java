package com.x.bff.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.x.bff.service.ServiceClientFactory;
import com.x.bff.utils.XUtil;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    public PaymentController(ServiceClientFactory clientFactory) {
        this.paymentClient = clientFactory.forService("payment", "/api/v1/payments");
    }

    @PostMapping
    @PreAuthorize("hasAuthority('x-payment:create')")
    public Mono<ResponseEntity<?>> create(@RequestBody JsonNode request) {
        return paymentClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> XUtil.toJsonResponse(body, response.statusCode())));
    }
}
