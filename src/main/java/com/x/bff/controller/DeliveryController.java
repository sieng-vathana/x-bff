package com.x.bff.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.x.bff.service.ServiceClientFactory;
import com.x.bff.utils.XUtil;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/deliveries")
public class DeliveryController {

    private final WebClient deliveryClient;

    public DeliveryController(ServiceClientFactory clientFactory) {
        this.deliveryClient = clientFactory.forService("delivery", "/api/v1/deliveries");
    }

    @PostMapping("/quotes")
    @PreAuthorize("hasAuthority('x-order:read') or hasAuthority('x-order:create') or hasAuthority('x-delivery:read')")
    public Mono<ResponseEntity<?>> createQuotes(@RequestBody JsonNode request) {
        return forward(deliveryClient.post().uri("/quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request));
    }

    @GetMapping("/quotes/{id}")
    @PreAuthorize("hasAuthority('x-order:read') or hasAuthority('x-order:create') or hasAuthority('x-delivery:read')")
    public Mono<ResponseEntity<?>> getActiveQuote(@PathVariable Long id) {
        return forward(deliveryClient.get().uri("/quotes/{id}", id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('x-order:update') or hasAuthority('x-order:create') or hasAuthority('x-delivery:create')")
    public Mono<ResponseEntity<?>> create(@RequestBody JsonNode request) {
        return forward(deliveryClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('x-order:read') or hasAuthority('x-delivery:read')")
    public Mono<ResponseEntity<?>> getDeliveries(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String providerType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return forward(deliveryClient.get()
                .uri(uri -> {
                    var builder = uri
                            .queryParam("page", page)
                            .queryParam("size", size);
                    if (storeId != null) builder.queryParam("storeId", storeId);
                    if (orderId != null) builder.queryParam("orderId", orderId);
                    if (status != null && !status.isBlank()) builder.queryParam("status", status);
                    if (providerType != null && !providerType.isBlank()) builder.queryParam("providerType", providerType);
                    if (search != null && !search.isBlank()) builder.queryParam("search", search);
                    return builder.build();
                }));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('x-order:read') or hasAuthority('x-delivery:read')")
    public Mono<ResponseEntity<?>> get(@PathVariable Long id) {
        return forward(deliveryClient.get().uri("/{id}", id));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('x-order:update') or hasAuthority('x-order:create') or hasAuthority('x-delivery:update')")
    public Mono<ResponseEntity<?>> updateStatus(@PathVariable Long id, @RequestBody JsonNode request) {
        return forward(deliveryClient.post().uri("/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request));
    }

    @PostMapping("/{id}/remittance")
    @PreAuthorize("hasAuthority('x-order:update') or hasAuthority('x-order:create') or hasAuthority('x-delivery:update')")
    public Mono<ResponseEntity<?>> recordRemittance(@PathVariable Long id, @RequestBody JsonNode request) {
        return forward(deliveryClient.post().uri("/{id}/remittance", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request));
    }

    private Mono<ResponseEntity<?>> forward(WebClient.RequestHeadersSpec<?> request) {
        return request.exchangeToMono(response -> response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> XUtil.toJsonResponse(body, response.statusCode())));
    }
}
