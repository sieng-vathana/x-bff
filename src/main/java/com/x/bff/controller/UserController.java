package com.x.bff.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.x.bff.dto.BusinessResponse;
import com.x.bff.dto.StoreResponse;
import com.x.bff.dto.UserCredentialsResponse;
import com.x.bff.service.ServiceClientFactory;
import com.x.bff.service.UserServiceClient;
import com.x.bff.utils.XUtil;
import com.sharedlib.response.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final WebClient userClient;
    private final WebClient businessClient;
    private final WebClient storeClient;
    private final UserServiceClient userServiceClient;

    public UserController(ServiceClientFactory clientFactory, UserServiceClient userServiceClient) {
        this.userClient = clientFactory.forService("user", "");
        this.businessClient = clientFactory.forService("business", "/api/v1/businesses");
        this.storeClient = clientFactory.forService("store", "/api/v1/stores");
        this.userServiceClient = userServiceClient;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('x-user:read')")
    public Mono<ResponseEntity<?>> getUsers(
            @RequestParam Long businessId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(forward(userClient.get().uri(uri -> uri
                        .queryParam("businessId", businessId)
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())));
    }

    @GetMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAuthority('x-user:read')")
    public Mono<ResponseEntity<?>> getUserById(
            @PathVariable Long id,
            @RequestParam Long businessId,
            Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(forward(userClient.get().uri(uri -> uri
                        .path("/by-id/{id}")
                        .queryParam("businessId", businessId)
                        .build(id))));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('x-user:create')")
    public Mono<ResponseEntity<?>> createUser(
            @RequestBody JsonNode request,
            Authentication authentication) {
        Long businessId = requirePositiveBusinessId(request);
        return requireBusinessAccess(businessId, authentication)
                .then(requireStoresBelongToBusiness(request, businessId))
                .then(forward(userClient.post()
                        .uri("/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request)));
    }

    @PutMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAuthority('x-user:update')")
    public Mono<ResponseEntity<?>> updateUser(
            @PathVariable Long id,
            @RequestBody JsonNode request,
            Authentication authentication) {
        Long businessId = requirePositiveBusinessId(request);
        return requireBusinessAccess(businessId, authentication)
                .then(requireStoresBelongToBusiness(request, businessId))
                .then(forward(userClient.put().uri(uri -> uri.path("/{id}").build(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request)));
    }

    @DeleteMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAuthority('x-user:delete')")
    public Mono<ResponseEntity<?>> deleteUser(
            @PathVariable Long id,
            @RequestParam Long businessId,
            Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(forward(userClient.delete().uri(uri -> uri
                        .path("/{id}")
                        .queryParam("businessId", businessId)
                        .build(id))));
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('x-user:read') or hasAuthority('x-user:create')")
    public Mono<ResponseEntity<?>> getRoles(
            @RequestParam Long businessId,
            Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(forward(userClient.get().uri(uri -> uri
                        .path("/roles")
                        .queryParam("businessId", businessId)
                        .build())));
    }

    @GetMapping("/roles/{id:[0-9]+}")
    @PreAuthorize("hasAuthority('x-user:read') or hasAuthority('x-user:create')")
    public Mono<ResponseEntity<?>> getRoleDetails(
            @PathVariable Long id,
            @RequestParam Long businessId,
            Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(forward(userClient.get().uri(uri -> uri
                        .path("/roles/{id}")
                        .queryParam("businessId", businessId)
                        .build(id))));
    }

    private Long requirePositiveBusinessId(JsonNode request) {
        JsonNode value = request.get("businessId");
        if (value == null || !value.canConvertToLong() || value.longValue() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "businessId must be positive");
        }
        return value.longValue();
    }

    private Mono<Void> requireStoresBelongToBusiness(JsonNode request, Long businessId) {
        JsonNode storeIdsNode = request.get("storeIds");
        if (storeIdsNode == null || !storeIdsNode.isArray() || storeIdsNode.isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one store"));
        }
        List<Long> storeIds = new ArrayList<>();
        storeIdsNode.forEach(node -> {
            if (!node.canConvertToLong() || node.longValue() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeIds must contain positive IDs");
            }
            storeIds.add(node.longValue());
        });
        return Flux.fromIterable(storeIds.stream().distinct().toList())
                .flatMap(storeId -> storeClient.get()
                        .uri("/{id}", storeId)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<ApiResponse<StoreResponse>>() {})
                        .map(ApiResponse::getData)
                        .filter(store -> Objects.equals(store.businessId(), businessId))
                        .switchIfEmpty(Mono.error(new AccessDeniedException(
                                "Store does not belong to the selected business"))))
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
                        .switchIfEmpty(Mono.error(new AccessDeniedException(
                                "User does not have access to this business")));
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
