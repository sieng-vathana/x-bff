package com.x.bff.controller;

import com.x.bff.dto.BusinessResponse;
import com.x.bff.dto.CreateStoreRequest;
import com.x.bff.dto.StoreResponse;
import com.x.bff.dto.UserCredentialsResponse;
import com.x.bff.service.ServiceClientFactory;
import com.x.bff.service.UserServiceClient;
import com.sharedlib.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Objects;

@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {

    private final WebClient storeClient;
    private final WebClient businessClient;
    private final UserServiceClient userServiceClient;

    public StoreController(ServiceClientFactory clientFactory, UserServiceClient userServiceClient) {
        this.storeClient = clientFactory.forService("store", "/api/v1/stores");
        this.businessClient = clientFactory.forService("business", "/api/v1/businesses");
        this.userServiceClient = userServiceClient;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('x-store:create')")
    public Mono<ResponseEntity<ApiResponse<StoreResponse>>> create(
            @Valid @RequestBody CreateStoreRequest request,
            Authentication authentication) {
        return requireBusinessOwnership(request.businessId(), authentication)
                .then(storeClient.post()
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<ApiResponse<StoreResponse>>() {})
                        .map(ApiResponse::getData))
                .map(response -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(HttpStatus.CREATED.value(), "Store created", response)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('x-store:read')")
    public Mono<ResponseEntity<ApiResponse<StoreResponse>>> getById(
            @PathVariable Long id,
            Authentication authentication) {
        return storeClient.get()
                        .uri("/{id}", id)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<ApiResponse<StoreResponse>>() {})
                        .map(ApiResponse::getData)
                .flatMap(store -> requireBusinessOwnership(store.businessId(), authentication).thenReturn(store))
                .map(store -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), store)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('x-store:read')")
    public Mono<ResponseEntity<ApiResponse<java.util.List<StoreResponse>>>> getByBusiness(
            @RequestParam Long businessId,
            Authentication authentication) {
        return requireBusinessOwnership(businessId, authentication)
                .then(storeClient.get()
                        .uri(uriBuilder -> uriBuilder.queryParam("businessId", businessId).build())
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<ApiResponse<java.util.List<StoreResponse>>>() {})
                        .map(ApiResponse::getData))
                .map(stores -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), stores)));
    }

    private Mono<Void> requireBusinessOwnership(Long businessId, Authentication authentication) {
        return currentUser(authentication)
                .flatMap(user -> businessClient.get()
                        .uri("/{id}", businessId)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<ApiResponse<BusinessResponse>>() {})
                        .map(ApiResponse::getData)
                        .filter(business -> Objects.equals(user.getId(), business.ownerUserId()))
                        .switchIfEmpty(Mono.error(new AccessDeniedException(
                                "Business does not belong to the authenticated user"))))
                .then();
    }

    private Mono<UserCredentialsResponse> currentUser(Authentication authentication) {
        return userServiceClient.findByUsername(authentication.getName());
    }
}
