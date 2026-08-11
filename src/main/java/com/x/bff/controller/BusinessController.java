package com.x.bff.controller;

import com.x.bff.dto.BusinessResponse;
import com.x.bff.dto.CreateBusinessRequest;
import com.x.bff.dto.UserCredentialsResponse;
import com.x.bff.service.ServiceClientFactory;
import com.x.bff.service.UserServiceClient;
import com.sharedlib.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
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
    @PreAuthorize("hasAuthority('x-business:create')")
    public Mono<ResponseEntity<ApiResponse<BusinessResponse>>> create(
            @Valid @RequestBody CreateBusinessRequest request,
            Authentication authentication) {
        return currentUser(authentication)
                .flatMap(user -> businessClient.post()
                        .bodyValue(new CreateBusinessCommand(
                                user.getId(),
                                request.name(),
                                request.code(),
                                request.defaultCurrencyCode(),
                                request.taxRegistrationNumber(),
                                request.taxRegistrationLabel(),
                                request.defaultTaxId(),
                                request.pricesIncludeTax(),
                                request.timeZone(),
                                request.fiscalYearStartMonth()))
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<ApiResponse<BusinessResponse>>() {})
                        .map(ApiResponse::getData)
                        .flatMap(response -> userServiceClient.ensureOwnerBusinessAccess(
                                        user.getId(), response.id(), List.of())
                                .thenReturn(response)))
                .map(response -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(HttpStatus.CREATED.value(), "Business created", response)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('x-business:update')")
    public Mono<ResponseEntity<ApiResponse<BusinessResponse>>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBusinessRequest request,
            Authentication authentication) {
        return requireBusinessAccess(id, authentication)
                .then(businessClient.put()
                        .uri("/{id}", id)
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<ApiResponse<BusinessResponse>>() {})
                        .map(ApiResponse::getData))
                .map(business -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Business updated", business)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('x-business:read')")
    public Mono<ResponseEntity<ApiResponse<BusinessResponse>>> getById(
            @PathVariable Long id,
            Authentication authentication) {
        return requireBusinessAccess(id, authentication)
                .then(businessClient.get()
                        .uri("/{id}", id)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<ApiResponse<BusinessResponse>>() {})
                        .map(ApiResponse::getData))
                .map(business -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), business)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('x-business:read')")
    public Mono<ResponseEntity<ApiResponse<java.util.List<BusinessResponse>>>> getMine(Authentication authentication) {
        return currentUser(authentication)
                .flatMap(user -> businessClient.get()
                        .uri(uriBuilder -> uriBuilder.queryParam("ownerUserId", user.getId()).build())
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<ApiResponse<java.util.List<BusinessResponse>>>() {})
                        .map(ApiResponse::getData))
                .map(businesses -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), businesses)));
    }

    private Mono<UserCredentialsResponse> currentUser(Authentication authentication) {
        return userServiceClient.findByUsername(authentication.getName());
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
                            .switchIfEmpty(Mono.error(new org.springframework.security.access.AccessDeniedException(
                                    "User does not have access to this business")));
                })
                .then();
    }

    private record UpdateBusinessRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 160) String name) {
    }

    private record CreateBusinessCommand(
            Long ownerUserId,
            String name,
            String code,
            String defaultCurrencyCode,
            String taxRegistrationNumber,
            String taxRegistrationLabel,
            Long defaultTaxId,
            Boolean pricesIncludeTax,
            String timeZone,
            Integer fiscalYearStartMonth) {
    }
}
