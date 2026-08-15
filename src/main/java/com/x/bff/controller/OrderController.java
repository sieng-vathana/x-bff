package com.x.bff.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.x.bff.service.ServiceClientFactory;
import com.x.bff.utils.XUtil;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Locale;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final WebClient orderClient;
    private final WebClient paymentClient;
    private final ObjectMapper objectMapper;

    public OrderController(ServiceClientFactory clientFactory, ObjectMapper objectMapper) {
        this.orderClient = clientFactory.forService("order", "/api/v1/orders");
        this.paymentClient = clientFactory.forService("payment", "/api/v1/payments");
        this.objectMapper = objectMapper;
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
                .exchangeToMono(this::enrichRecentOrders);
    }

    @PostMapping("/pos")
    @PreAuthorize("hasAuthority('x-order:create')")
    public Mono<ResponseEntity<?>> createPos(@RequestBody JsonNode request) {
        return forward(orderClient.post().uri("/pos")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request));
    }

    @GetMapping("/holds")
    @PreAuthorize("hasAuthority('x-order:read')")
    public Mono<ResponseEntity<?>> getHeldOrders(
            @RequestParam Long storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return forward(orderClient.get()
                .uri(uri -> uri.path("/holds")
                        .queryParam("storeId", storeId)
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build()));
    }

    @PostMapping("/holds")
    @PreAuthorize("hasAuthority('x-order:create')")
    public Mono<ResponseEntity<?>> createHeld(@RequestBody JsonNode request) {
        return forward(orderClient.post().uri("/holds")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request));
    }

    @PostMapping("/holds/{id}/resume")
    @PreAuthorize("hasAuthority('x-order:update') or hasAuthority('x-order:create')")
    public Mono<ResponseEntity<?>> resumeHeld(@PathVariable Long id) {
        return forward(orderClient.post().uri("/holds/{id}/resume", id));
    }

    @PostMapping("/holds/{id}/discard")
    @PreAuthorize("hasAuthority('x-order:update') or hasAuthority('x-order:create')")
    public Mono<ResponseEntity<?>> discardHeld(@PathVariable Long id) {
        return forward(orderClient.post().uri("/holds/{id}/discard", id));
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

    private Mono<ResponseEntity<?>> enrichRecentOrders(org.springframework.web.reactive.function.client.ClientResponse response) {
        HttpStatusCode status = response.statusCode();
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    if (!status.is2xxSuccessful()) {
                        return Mono.just(XUtil.toJsonResponse(body, status));
                    }
                    return enrichPaymentMethods(body)
                            .map(enrichedBody -> XUtil.toJsonResponse(enrichedBody, status));
                });
    }

    private Mono<String> enrichPaymentMethods(String rawBody) {
        if (rawBody.isBlank()) {
            return Mono.just(rawBody);
        }

        final JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (JsonProcessingException exception) {
            return Mono.just(rawBody);
        }

        if (!(root instanceof ObjectNode rootObject)
                || !(rootObject.get("data") instanceof ObjectNode dataObject)
                || !(dataObject.get("content") instanceof ArrayNode content)) {
            return Mono.just(rawBody);
        }

        ArrayNode enrichedContent = objectMapper.createArrayNode();
        return Flux.fromIterable(content)
                .flatMapSequential(this::enrichOrderPaymentMethod, 4)
                .doOnNext(enrichedContent::add)
                .then(Mono.fromCallable(() -> {
                    ObjectNode enrichedRoot = rootObject.deepCopy();
                    ObjectNode enrichedData = (ObjectNode) enrichedRoot.get("data");
                    enrichedData.set("content", enrichedContent);
                    return objectMapper.writeValueAsString(enrichedRoot);
                }))
                .onErrorReturn(rawBody);
    }

    private Mono<JsonNode> enrichOrderPaymentMethod(JsonNode order) {
        if (!(order instanceof ObjectNode orderObject)) {
            return Mono.just(order);
        }

        long orderId = order.path("id").asLong(0);
        if (orderId <= 0) {
            return Mono.just(order);
        }

        return paymentClient.get()
                .uri(uri -> uri.queryParam("orderId", orderId).build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(paymentResponse -> {
                    String method = findPaymentMethod(paymentResponse);
                    if (method == null) {
                        orderObject.putNull("paymentMethod");
                    } else {
                        orderObject.put("paymentMethod", method);
                    }
                    return (JsonNode) orderObject;
                })
                .defaultIfEmpty(orderObject)
                .onErrorReturn(orderObject);
    }

    private String findPaymentMethod(JsonNode paymentResponse) {
        JsonNode payments = paymentResponse.path("data");
        if (!payments.isArray()) {
            return null;
        }

        String fallback = null;
        for (JsonNode payment : payments) {
            String method = payment.path("method").asText(null);
            if (method == null || method.isBlank()) {
                continue;
            }
            fallback = method;
            if (isSettled(payment.path("status").asText())) {
                return method;
            }
        }
        return fallback;
    }

    private boolean isSettled(String status) {
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "PAID", "PARTIALLY_REFUNDED", "REFUNDED" -> true;
            default -> false;
        };
    }
}
