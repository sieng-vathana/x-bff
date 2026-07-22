package com.x.bff.controller;

import com.x.bff.dto.BusinessResponse;
import com.x.bff.dto.CreateShopRequest;
import com.x.bff.dto.ShopResponse;
import com.x.bff.dto.UserCredentialsResponse;
import com.x.bff.service.ServiceClientFactory;
import com.x.bff.service.UserServiceClient;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/shops")
public class ShopController {

    private final WebClient shopClient;
    private final WebClient businessClient;
    private final UserServiceClient userServiceClient;

    public ShopController(ServiceClientFactory clientFactory, UserServiceClient userServiceClient) {
        this.shopClient = clientFactory.forService("shop", "/api/v1/shops");
        this.businessClient = clientFactory.forService("business", "/api/v1/businesses");
        this.userServiceClient = userServiceClient;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('x-shop:create')")
    public Mono<ResponseEntity<ShopResponse>> create(
            @Valid @RequestBody CreateShopRequest request,
            Authentication authentication) {
        return requireBusinessOwnership(request.businessId(), authentication)
                .then(shopClient.post()
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(ShopResponse.class))
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('x-shop:read')")
    public Mono<ResponseEntity<ShopResponse>> getById(
            @PathVariable Long id,
            Authentication authentication) {
        return shopClient.get()
                .uri("/{id}", id)
                .retrieve()
                .bodyToMono(ShopResponse.class)
                .flatMap(shop -> requireBusinessOwnership(shop.businessId(), authentication).thenReturn(shop))
                .map(ResponseEntity::ok);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('x-shop:read')")
    public Mono<ResponseEntity<java.util.List<ShopResponse>>> getByBusiness(
            @RequestParam Long businessId,
            Authentication authentication) {
        return requireBusinessOwnership(businessId, authentication)
                .then(shopClient.get()
                        .uri(uriBuilder -> uriBuilder.queryParam("businessId", businessId).build())
                        .retrieve()
                        .bodyToFlux(ShopResponse.class)
                        .collectList())
                .map(ResponseEntity::ok);
    }

    private Mono<Void> requireBusinessOwnership(Long businessId, Authentication authentication) {
        return currentUser(authentication)
                .flatMap(user -> businessClient.get()
                        .uri("/{id}", businessId)
                        .retrieve()
                        .bodyToMono(BusinessResponse.class)
                        .filter(business -> Objects.equals(user.getId(), business.ownerUserId()))
                        .switchIfEmpty(Mono.error(new AccessDeniedException(
                                "Business does not belong to the authenticated user"))))
                .then();
    }

    private Mono<UserCredentialsResponse> currentUser(Authentication authentication) {
        return userServiceClient.findByUsername(authentication.getName());
    }
}
