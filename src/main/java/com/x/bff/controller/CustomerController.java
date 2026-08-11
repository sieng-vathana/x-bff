package com.x.bff.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sharedlib.response.ApiResponse;
import com.x.bff.dto.BusinessResponse;
import com.x.bff.dto.UserCredentialsResponse;
import com.x.bff.service.ServiceClientFactory;
import com.x.bff.service.UserServiceClient;
import com.x.bff.utils.XUtil;
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
import reactor.core.publisher.Mono;

import java.util.Objects;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final WebClient customerClient;
    private final WebClient businessClient;
    private final UserServiceClient userServiceClient;

    public CustomerController(ServiceClientFactory clientFactory, UserServiceClient userServiceClient) {
        this.customerClient = clientFactory.forService("customer", "/api/v1/customers");
        this.businessClient = clientFactory.forService("business", "/api/v1/businesses");
        this.userServiceClient = userServiceClient;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('x-customer:read')")
    public Mono<ResponseEntity<?>> list(
            @RequestParam Long businessId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(forward(customerClient.get().uri(uri -> uri
                        .queryParam("businessId", businessId)
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())));
    }

    @GetMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAuthority('x-customer:read')")
    public Mono<ResponseEntity<?>> get(
            @PathVariable Long id,
            @RequestParam Long businessId,
            Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(forward(customerClient.get().uri(uri -> uri
                        .path("/{id}")
                        .queryParam("businessId", businessId)
                        .build(id))));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('x-customer:create')")
    public Mono<ResponseEntity<?>> create(
            @RequestBody JsonNode request,
            Authentication authentication) {
        return requestBusinessId(request)
                .flatMap(businessId -> requireBusinessAccess(businessId, authentication)
                        .then(forward(customerClient.post()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(request))));
    }

    @PutMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAuthority('x-customer:update')")
    public Mono<ResponseEntity<?>> update(
            @PathVariable Long id,
            @RequestParam Long businessId,
            @RequestBody JsonNode request,
            Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(forward(customerClient.put().uri(uri -> uri
                        .path("/{id}")
                        .queryParam("businessId", businessId)
                        .build(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request)));
    }

    @DeleteMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAuthority('x-customer:delete')")
    public Mono<ResponseEntity<?>> deactivate(
            @PathVariable Long id,
            @RequestParam Long businessId,
            Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(forward(customerClient.delete().uri(uri -> uri
                        .path("/{id}")
                        .queryParam("businessId", businessId)
                        .build(id))));
    }

    private Mono<Long> requestBusinessId(JsonNode request) {
        if (request == null || !request.hasNonNull("businessId") || request.get("businessId").asLong() <= 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "businessId is required"));
        }
        return Mono.just(request.get("businessId").asLong());
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
                                    ApiResponse<BusinessResponse>>() {})
                            .map(ApiResponse::getData)
                            .filter(business -> Objects.equals(user.getId(), business.ownerUserId()))
                            .switchIfEmpty(Mono.error(new AccessDeniedException(
                                    "User does not have access to this business")))
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
