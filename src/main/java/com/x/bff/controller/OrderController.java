package com.x.bff.controller;

import com.x.bff.service.ServiceClientFactory;
import com.x.bff.utils.XUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final WebClient orderClient;

    public OrderController(ServiceClientFactory clientFactory) {
        this.orderClient = clientFactory.forService("order", "/api/v1/orders");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('x-order:read')")
    public Mono<ResponseEntity<?>> getOrders() {
        return orderClient.get()
                .retrieve()
                .bodyToMono(String.class)
                .map(XUtil::toJsonResponse);
    }

    @GetMapping("/echo/{text}")
    @PreAuthorize("hasAuthority('x-order:read')")
    public Mono<ResponseEntity<?>> echoText(@org.springframework.web.bind.annotation.PathVariable String text) {
        return orderClient.get()
                .uri("/echo/{text}", text)
                .retrieve()
                .bodyToMono(String.class)
                .map(XUtil::toJsonResponse);
    }
}
