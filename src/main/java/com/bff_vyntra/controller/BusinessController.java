package com.bff_vyntra.controller;

import com.bff_vyntra.dto.BusinessResponse;
import com.bff_vyntra.dto.CreateBusinessRequest;
import com.bff_vyntra.dto.UserCredentialsResponse;
import com.bff_vyntra.service.ServiceClientFactory;
import com.bff_vyntra.service.UserServiceClient;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Objects;

@RestController
@RequestMapping("/api/v1/businesses")
public class BusinessController {

    private final WebClient businessClient;
    private final UserServiceClient userServiceClient;

    public BusinessController(ServiceClientFactory clientFactory, UserServiceClient userServiceClient) {
        this.businessClient = clientFactory.forService("business", "/api/v1/businesses");
        this.userServiceClient = userServiceClient;
    }

    @PostMapping
    public Mono<ResponseEntity<BusinessResponse>> create(
            @Valid @RequestBody CreateBusinessRequest request,
            Authentication authentication) {
        return currentUser(authentication)
                .flatMap(user -> businessClient.post()
                        .bodyValue(new CreateBusinessCommand(user.getId(), request.name(), request.code()))
                        .retrieve()
                        .bodyToMono(BusinessResponse.class))
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<BusinessResponse>> getById(
            @PathVariable Long id,
            Authentication authentication) {
        return currentUser(authentication)
                .flatMap(user -> businessClient.get()
                        .uri("/{id}", id)
                        .retrieve()
                        .bodyToMono(BusinessResponse.class)
                        .filter(business -> Objects.equals(user.getId(), business.ownerUserId()))
                        .switchIfEmpty(Mono.error(new org.springframework.security.access.AccessDeniedException(
                                "Business does not belong to the authenticated user"))))
                .map(ResponseEntity::ok);
    }

    @GetMapping
    public Mono<ResponseEntity<java.util.List<BusinessResponse>>> getMine(Authentication authentication) {
        return currentUser(authentication)
                .flatMap(user -> businessClient.get()
                        .uri(uriBuilder -> uriBuilder.queryParam("ownerUserId", user.getId()).build())
                        .retrieve()
                        .bodyToFlux(BusinessResponse.class)
                        .collectList())
                .map(ResponseEntity::ok);
    }

    private Mono<UserCredentialsResponse> currentUser(Authentication authentication) {
        return userServiceClient.findByUsername(authentication.getName());
    }

    private record CreateBusinessCommand(Long ownerUserId, String name, String code) {
    }
}
