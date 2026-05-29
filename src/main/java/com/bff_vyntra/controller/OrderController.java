package com.bff_vyntra.controller;

import com.bff_vyntra.service.ServiceClientFactory;
import com.bff_vyntra.utils.VyntraUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping({ "/api/v1/orders" })
@RequiredArgsConstructor
public class OrderController {

    private final ServiceClientFactory clientFactory;
    private WebClient orderClient;

    @PostConstruct
    void init() {
        this.orderClient = clientFactory.forService("/api/v1/orders");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ORDERS')")
    public Mono<ResponseEntity<?>> getOrders() {
        return orderClient.get()
                .retrieve()
                .bodyToMono(String.class)
                .map(VyntraUtil::toJsonResponse);
    }

    @GetMapping("/echo/{text}")
    public Mono<ResponseEntity<?>> echoText(@org.springframework.web.bind.annotation.PathVariable String text) {
        return orderClient.get()
                .uri("/echo/{text}", text)
                .retrieve()
                .bodyToMono(String.class)
                .map(VyntraUtil::toJsonResponse);
    }
}
