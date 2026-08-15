package com.x.bff.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.x.bff.service.ServiceClientFactory;
import com.x.bff.utils.XUtil;
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
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final WebClient orderClient;

    public OrderController(ServiceClientFactory clientFactory) {
        this.orderClient = clientFactory.forService("order", "/api/v1/orders");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('x-order:read')")
    public Mono<ResponseEntity<?>> getOrders(
            @RequestParam Long storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return orderClient.get()
                .uri(uri -> uri
                        .queryParam("storeId", storeId)
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(XUtil::toJsonResponse);
    }

    @PostMapping("/pos")
    @PreAuthorize("hasAuthority('x-order:create')")
    public Mono<ResponseEntity<?>> createPos(@RequestBody JsonNode request) {
        return forward(orderClient.post().uri("/pos")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('x-order:update') or hasAuthority('x-order:create')")
    public Mono<ResponseEntity<?>> complete(@PathVariable Long id) {
        return forward(orderClient.post().uri("/{id}/complete", id));
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

    private Mono<ResponseEntity<?>> forward(WebClient.RequestHeadersSpec<?> request) {
        return request.exchangeToMono(response -> response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> XUtil.toJsonResponse(body, response.statusCode())));
    }
}
