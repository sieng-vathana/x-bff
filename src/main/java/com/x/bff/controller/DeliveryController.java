package com.x.bff.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sharedlib.response.ApiResponse;
import com.x.bff.dto.StoreResponse;
import com.x.bff.dto.UserCredentialsResponse;
import com.x.bff.service.ServiceClientFactory;
import com.x.bff.service.UserServiceClient;
import com.x.bff.utils.XUtil;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
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
    private final WebClient storeClient;
    private final UserServiceClient userServiceClient;

    public DeliveryController(ServiceClientFactory clientFactory, UserServiceClient userServiceClient) {
        this.deliveryClient = clientFactory.forService("delivery", "/api/v1/deliveries");
        this.storeClient = clientFactory.forService("store", "/api/v1/stores");
        this.userServiceClient = userServiceClient;
    }

    @GetMapping("/providers")
    @PreAuthorize("hasAuthority('platform:admin') or hasAuthority('x-store:read')")
    public Mono<ResponseEntity<?>> getProviders() {
        return forward(deliveryClient.get().uri("/providers"));
    }

    @PutMapping("/providers")
    @PreAuthorize("hasAuthority('platform:admin')")
    public Mono<ResponseEntity<?>> updateProviders(@RequestBody JsonNode request) {
        return forward(deliveryClient.put().uri("/providers")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request));
    }

    @GetMapping("/stores/{storeId}/config")
    @PreAuthorize("hasAuthority('platform:admin') or hasAuthority('x-store:read')")
    public Mono<ResponseEntity<?>> getStoreConfig(@PathVariable Long storeId, Authentication authentication) {
        return requireStoreAccess(storeId, authentication)
                .then(forward(deliveryClient.get().uri("/stores/{storeId}/config", storeId)));
    }

    @PutMapping("/stores/{storeId}/config")
    @PreAuthorize("hasAuthority('platform:admin') or hasAuthority('x-store:update')")
    public Mono<ResponseEntity<?>> updateStoreConfig(
            @PathVariable Long storeId, @RequestBody JsonNode request, Authentication authentication) {
        return requireStoreAccess(storeId, authentication)
                .then(forward(deliveryClient.put().uri("/stores/{storeId}/config", storeId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)));
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

    private Mono<Void> requireStoreAccess(Long storeId, Authentication authentication) {
        if (isPlatformAdmin(authentication)) return Mono.empty();
        return storeClient.get().uri("/{id}", storeId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<StoreResponse>>() {})
                .map(ApiResponse::getData)
                .switchIfEmpty(Mono.error(new AccessDeniedException("Store was not found or is not accessible")))
                .flatMap(store -> userServiceClient.findByUsername(authentication.getName())
                        .flatMap(user -> hasStoreAccess(user, store)
                                ? Mono.empty()
                                : Mono.error(new AccessDeniedException("User does not have access to this store"))))
                .then();
    }

    private boolean hasStoreAccess(UserCredentialsResponse user, StoreResponse store) {
        return (user.getStoreIds() != null && user.getStoreIds().contains(store.id()))
                || (user.getBusinessIds() != null && user.getBusinessIds().contains(store.businessId()));
    }

    private boolean isPlatformAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "platform:admin".equals(authority.getAuthority()));
    }
}
