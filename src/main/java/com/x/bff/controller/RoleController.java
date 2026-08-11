package com.x.bff.controller;

import com.x.bff.dto.BusinessResponse;
import com.x.bff.dto.RoleUpsertRequest;
import com.x.bff.dto.UserCredentialsResponse;
import com.x.bff.service.ServiceClientFactory;
import com.x.bff.service.UserServiceClient;
import com.x.bff.utils.XUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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
import reactor.core.publisher.Mono;

import java.util.Objects;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final WebClient userClient;
    private final WebClient businessClient;
    private final UserServiceClient userServiceClient;

    public RoleController(ServiceClientFactory clientFactory, UserServiceClient userServiceClient) {
        this.userClient = clientFactory.forService("user", "");
        this.businessClient = clientFactory.forService("business", "/api/v1/businesses");
        this.userServiceClient = userServiceClient;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('x-user:read')")
    public Mono<ResponseEntity<?>> list(
            @RequestParam @Positive Long businessId,
            Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(forward(userClient.get().uri(uri -> uri
                        .path("/roles")
                        .queryParam("businessId", businessId)
                        .build())));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('x-user:read')")
    public Mono<ResponseEntity<?>> permissions(
            @RequestParam @Positive Long businessId,
            Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(forward(userClient.get().uri("/roles/permissions")));
    }

    @GetMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAuthority('x-user:read')")
    public Mono<ResponseEntity<?>> details(
            @PathVariable Long id,
            @RequestParam @Positive Long businessId,
            Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(forward(userClient.get().uri(uri -> uri
                        .path("/roles/{id}")
                        .queryParam("businessId", businessId)
                        .build(id))));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('x-user:create')")
    public Mono<ResponseEntity<?>> create(
            @Valid @RequestBody RoleUpsertRequest request,
            Authentication authentication) {
        return requireBusinessAccess(request.businessId(), authentication)
                .then(forward(userClient.post()
                        .uri("/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request)));
    }

    @PutMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAuthority('x-user:update')")
    public Mono<ResponseEntity<?>> update(
            @PathVariable Long id,
            @Valid @RequestBody RoleUpsertRequest request,
            Authentication authentication) {
        return requireBusinessAccess(request.businessId(), authentication)
                .then(forward(userClient.put()
                        .uri("/roles/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request)));
    }

    @DeleteMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAuthority('x-user:delete')")
    public Mono<ResponseEntity<?>> delete(
            @PathVariable Long id,
            @RequestParam @Positive Long businessId,
            Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(forward(userClient.delete().uri(uri -> uri
                        .path("/roles/{id}")
                        .queryParam("businessId", businessId)
                        .build(id))));
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
                        .bodyToMono(new org.springframework.core.ParameterizedTypeReference<
                                com.sharedlib.response.ApiResponse<BusinessResponse>>() {})
                        .map(com.sharedlib.response.ApiResponse::getData)
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
