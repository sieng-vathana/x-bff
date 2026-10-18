package com.x.bff.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sharedlib.response.ApiResponse;
import com.x.bff.dto.BusinessResponse;
import com.x.bff.dto.StoreResponse;
import com.x.bff.dto.UserCredentialsResponse;
import com.x.bff.service.ServiceClientFactory;
import com.x.bff.service.UserServiceClient;
import com.x.bff.utils.XUtil;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Suppliers, purchase orders and supplier returns. Purchases use the inventory permissions:
 * read to view, create and update to manage, stock-in to receive and stock-out to return goods.
 */
@RestController
@RequestMapping("/api/v1/purchases")
public class PurchaseController {

    private final WebClient inventoryClient;
    private final WebClient supplierClient;
    private final WebClient businessClient;
    private final WebClient storeClient;
    private final UserServiceClient userServiceClient;

    public PurchaseController(ServiceClientFactory clientFactory, UserServiceClient userServiceClient) {
        this.inventoryClient = clientFactory.forService("inventory", "/api/v1/inventory");
        this.supplierClient = clientFactory.forService("product", "/api/v1/products/suppliers");
        this.businessClient = clientFactory.forService("business", "/api/v1/businesses");
        this.storeClient = clientFactory.forService("store", "/api/v1/stores");
        this.userServiceClient = userServiceClient;
    }

    @GetMapping("/suppliers")
    @PreAuthorize("hasAuthority('x-inventory:read')")
    public Mono<ResponseEntity<?>> suppliers(@RequestParam Long businessId, Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(forward(supplierClient.get().uri(uri -> uri
                        .queryParam("businessId", businessId)
                        .queryParam("size", 100)
                        .build())));
    }

    @PostMapping("/suppliers")
    @PreAuthorize("hasAuthority('x-inventory:create')")
    public Mono<ResponseEntity<?>> createSupplier(@RequestBody JsonNode request, Authentication authentication) {
        Long businessId = requiredBusinessId(request);
        return requireBusinessAccess(businessId, authentication)
                .then(requireStoreIdsBelongToBusiness(request, businessId))
                .then(forward(supplierClient.post()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request)));
    }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('x-inventory:update')")
    public Mono<ResponseEntity<?>> updateSupplier(
            @PathVariable Long id, @RequestParam Long businessId, @RequestBody JsonNode request,
            Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(requireStoreIdsBelongToBusiness(request, businessId))
                .then(forward(supplierClient.put()
                        .uri(uri -> uri.path("/{id}").queryParam("businessId", businessId).build(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request)));
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('x-inventory:read')")
    public Mono<ResponseEntity<?>> orders(
            @RequestParam Long businessId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String status,
            Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(forward(inventoryClient.get().uri(uri -> {
                    var builder = uri.path("/purchase-orders").queryParam("businessId", businessId).queryParam("size", 100);
                    if (storeId != null) builder.queryParam("storeId", storeId);
                    if (status != null && !status.isBlank()) builder.queryParam("status", status);
                    return builder.build();
                })));
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAuthority('x-inventory:read')")
    public Mono<ResponseEntity<?>> order(@PathVariable Long id, @RequestParam Long businessId, Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(forward(inventoryClient.get().uri(uri -> uri
                        .path("/purchase-orders/{id}").queryParam("businessId", businessId).build(id))));
    }

    @PostMapping("/orders")
    @PreAuthorize("hasAuthority('x-inventory:create')")
    public Mono<ResponseEntity<?>> createOrder(@RequestBody JsonNode request, Authentication authentication) {
        return withUser(request, authentication, "createdBy")
                .flatMap(body -> forward(inventoryClient.post().uri("/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)));
    }

    @PutMapping("/orders/{id}")
    @PreAuthorize("hasAuthority('x-inventory:update')")
    public Mono<ResponseEntity<?>> updateOrder(@PathVariable Long id, @RequestBody JsonNode request, Authentication authentication) {
        return withUser(request, authentication, "createdBy")
                .flatMap(body -> forward(inventoryClient.put().uri("/purchase-orders/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)));
    }

    @PostMapping("/orders/{id}/place")
    @PreAuthorize("hasAuthority('x-inventory:update')")
    public Mono<ResponseEntity<?>> placeOrder(@PathVariable Long id, @RequestParam Long businessId, Authentication authentication) {
        return orderAction(id, businessId, "place", authentication);
    }

    @PostMapping("/orders/{id}/close")
    @PreAuthorize("hasAuthority('x-inventory:update')")
    public Mono<ResponseEntity<?>> closeOrder(@PathVariable Long id, @RequestParam Long businessId, Authentication authentication) {
        return orderAction(id, businessId, "close", authentication);
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("hasAuthority('x-inventory:update')")
    public Mono<ResponseEntity<?>> cancelOrder(@PathVariable Long id, @RequestParam Long businessId, Authentication authentication) {
        return orderAction(id, businessId, "cancel", authentication);
    }

    /** Adds the received quantities to the store's stock. */
    @PostMapping("/orders/{id}/receive")
    @PreAuthorize("hasAuthority('x-inventory:stock-in')")
    public Mono<ResponseEntity<?>> receive(
            @PathVariable Long id, @RequestParam Long businessId, @RequestBody JsonNode request,
            Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(currentUser(authentication))
                .flatMap(user -> {
                    ObjectNode body = objectBody(request);
                    body.put("performedBy", user.getId());
                    return forward(inventoryClient.post()
                            .uri(uri -> uri.path("/purchase-orders/{id}/receive").queryParam("businessId", businessId).build(id))
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(body));
                });
    }

    @GetMapping("/returns")
    @PreAuthorize("hasAuthority('x-inventory:read')")
    public Mono<ResponseEntity<?>> returns(
            @RequestParam Long businessId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String status,
            Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(forward(inventoryClient.get().uri(uri -> {
                    var builder = uri.path("/supplier-returns").queryParam("businessId", businessId).queryParam("size", 100);
                    if (storeId != null) builder.queryParam("storeId", storeId);
                    if (status != null && !status.isBlank()) builder.queryParam("status", status);
                    return builder.build();
                })));
    }

    @PostMapping("/returns")
    @PreAuthorize("hasAuthority('x-inventory:create')")
    public Mono<ResponseEntity<?>> createReturn(@RequestBody JsonNode request, Authentication authentication) {
        return withUser(request, authentication, "createdBy")
                .flatMap(body -> forward(inventoryClient.post().uri("/supplier-returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)));
    }

    /** The goods went back to the supplier: removes them from the store's stock. */
    @PostMapping("/returns/{id}/complete")
    @PreAuthorize("hasAuthority('x-inventory:stock-out')")
    public Mono<ResponseEntity<?>> completeReturn(@PathVariable Long id, @RequestParam Long businessId, Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(currentUser(authentication))
                .flatMap(user -> forward(inventoryClient.post().uri(uri -> uri
                        .path("/supplier-returns/{id}/complete")
                        .queryParam("businessId", businessId)
                        .queryParam("performedBy", user.getId())
                        .build(id))));
    }

    @PostMapping("/returns/{id}/cancel")
    @PreAuthorize("hasAuthority('x-inventory:update')")
    public Mono<ResponseEntity<?>> cancelReturn(@PathVariable Long id, @RequestParam Long businessId, Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(forward(inventoryClient.post().uri(uri -> uri
                        .path("/supplier-returns/{id}/cancel").queryParam("businessId", businessId).build(id))));
    }

    private Mono<ResponseEntity<?>> orderAction(Long id, Long businessId, String action, Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(forward(inventoryClient.post().uri(uri -> uri
                        .path("/purchase-orders/{id}/" + action).queryParam("businessId", businessId).build(id))));
    }

    /** Checks access to the body's business and records the signed-in user as the one acting. */
    private Mono<ObjectNode> withUser(JsonNode request, Authentication authentication, String userField) {
        Long businessId = requiredBusinessId(request);
        return requireBusinessAccess(businessId, authentication)
                .then(requireStoreBelongsToBusiness(requiredStoreId(request), businessId))
                .then(currentUser(authentication))
                .map(user -> {
                    ObjectNode body = objectBody(request);
                    body.put(userField, user.getId());
                    return body;
                });
    }

    private static ObjectNode objectBody(JsonNode request) {
        if (request == null || !request.isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A JSON object is required");
        }
        return ((ObjectNode) request).deepCopy();
    }

    private static Long requiredBusinessId(JsonNode request) {
        if (request == null || !request.hasNonNull("businessId") || request.get("businessId").asLong() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "businessId is required");
        }
        return request.get("businessId").asLong();
    }

    private static Long requiredStoreId(JsonNode request) {
        if (request == null || !request.hasNonNull("storeId") || request.get("storeId").asLong() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeId is required");
        }
        return request.get("storeId").asLong();
    }

    private Mono<Void> requireStoreIdsBelongToBusiness(JsonNode request, Long businessId) {
        JsonNode storeIdsNode = request.get("storeIds");
        if (storeIdsNode == null || storeIdsNode.isNull()) return Mono.empty();
        if (!storeIdsNode.isArray()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeIds must be an array"));
        }
        List<Long> storeIds = new ArrayList<>();
        for (JsonNode node : storeIdsNode) {
            if (!node.canConvertToLong() || node.longValue() <= 0) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeIds must contain positive IDs"));
            }
            storeIds.add(node.longValue());
        }
        return Flux.fromIterable(storeIds.stream().distinct().toList())
                .flatMap(storeId -> requireStoreBelongsToBusiness(storeId, businessId))
                .then();
    }

    private Mono<Void> requireStoreBelongsToBusiness(Long storeId, Long businessId) {
        return storeClient.get()
                .uri("/{id}", storeId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<StoreResponse>>() {})
                .map(ApiResponse::getData)
                .filter(store -> Objects.equals(store.businessId(), businessId))
                .switchIfEmpty(Mono.error(new AccessDeniedException("Store does not belong to the selected business")))
                .then();
    }

    private Mono<Void> requireBusinessAccess(Long businessId, Authentication authentication) {
        return currentUser(authentication)
                .flatMap(user -> {
                    if (user.getBusinessIds() != null && user.getBusinessIds().contains(businessId)) {
                        return Mono.empty();
                    }
                    return businessClient.get()
                            .uri("/{id}", businessId)
                            .retrieve()
                            .bodyToMono(new ParameterizedTypeReference<ApiResponse<BusinessResponse>>() {})
                            .map(ApiResponse::getData)
                            .filter(business -> Objects.equals(user.getId(), business.ownerUserId()))
                            .switchIfEmpty(Mono.error(new AccessDeniedException("User does not have access to this business")))
                            .then();
                })
                .then();
    }

    private Mono<UserCredentialsResponse> currentUser(Authentication authentication) {
        return userServiceClient.findByUsername(authentication.getName());
    }

    private Mono<ResponseEntity<?>> forward(WebClient.RequestHeadersSpec<?> request) {
        return request.exchangeToMono(response -> response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> XUtil.toJsonResponse(body, response.statusCode())));
    }
}
