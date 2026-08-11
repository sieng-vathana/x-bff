package com.x.bff.controller;

import com.x.bff.dto.BusinessResponse;
import com.x.bff.dto.CreateStoreRequest;
import com.x.bff.dto.StoreResponse;
import com.x.bff.dto.UpdateStoreRequest;
import com.x.bff.dto.UserCredentialsResponse;
import com.x.bff.service.ServiceClientFactory;
import com.x.bff.service.StoreImageUrlResolver;
import com.x.bff.service.UserServiceClient;
import com.sharedlib.response.ApiResponse;
import com.sharedlib.response.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
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
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {

    private final WebClient storeClient;
    private final WebClient businessClient;
    private final StoreImageUrlResolver storeImageUrlResolver;
    private final UserServiceClient userServiceClient;

    public StoreController(
            ServiceClientFactory clientFactory,
            UserServiceClient userServiceClient,
            StoreImageUrlResolver storeImageUrlResolver) {
        this.storeClient = clientFactory.forService("store", "/api/v1/stores");
        this.businessClient = clientFactory.forService("business", "/api/v1/businesses");
        this.userServiceClient = userServiceClient;
        this.storeImageUrlResolver = storeImageUrlResolver;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('x-store:create')")
    public Mono<ResponseEntity<ApiResponse<StoreResponse>>> create(
            @Valid @RequestBody CreateStoreRequest request,
            Authentication authentication) {
        return withStorageUrls(request)
                .flatMap(resolvedRequest -> requireBusinessAccess(resolvedRequest.businessId(), authentication)
                .then(storeClient.post()
                        .bodyValue(resolvedRequest)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<ApiResponse<StoreResponse>>() {})
                        .map(ApiResponse::getData)
                        .flatMap(storeImageUrlResolver::resolveResponse)
                        .flatMap(response -> findBusiness(response.businessId())
                                .flatMap(business -> userServiceClient.ensureOwnerBusinessAccess(
                                                business.ownerUserId(), response.businessId(), List.of(response.id()))
                                        .thenReturn(response)))))
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
                .flatMap(store -> requireBusinessAccess(store.businessId(), authentication)
                        .then(storeImageUrlResolver.resolveResponse(store)))
                .map(store -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), store)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('x-store:update')")
    public Mono<ResponseEntity<ApiResponse<StoreResponse>>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStoreRequest request,
            Authentication authentication) {
        return withStorageUrls(request)
                .flatMap(resolvedRequest -> storeClient.get()
                .uri("/{id}", id)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<StoreResponse>>() {})
                .map(ApiResponse::getData)
                .flatMap(store -> requireBusinessAccess(store.businessId(), authentication)
                        .then(storeClient.put()
                                .uri("/{id}", id)
                                .bodyValue(resolvedRequest)
                                .retrieve()
                                .bodyToMono(new ParameterizedTypeReference<ApiResponse<StoreResponse>>() {})
                                .map(ApiResponse::getData)
                                .flatMap(storeImageUrlResolver::resolveResponse))))
                .map(store -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Store updated", store)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('x-store:read')")
    public Mono<ResponseEntity<ApiResponse<PageResponse<StoreResponse>>>> getByBusiness(
            @RequestParam Long businessId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(storeClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .queryParam("businessId", businessId)
                                .queryParam("page", page)
                                .queryParam("size", size)
                                .build())
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<ApiResponse<PageResponse<StoreResponse>>>() {})
                        .map(ApiResponse::getData)
                        .flatMap(storeImageUrlResolver::resolvePage))
                .map(stores -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), stores)));
    }

    private Mono<Void> requireBusinessAccess(Long businessId, Authentication authentication) {
        return currentUser(authentication)
                .flatMap(user -> {
                    if (user.getBusinessIds() != null && user.getBusinessIds().contains(businessId)) {
                        return Mono.empty();
                    }
                    return findBusiness(businessId)
                            .filter(business -> Objects.equals(user.getId(), business.ownerUserId()))
                            .switchIfEmpty(Mono.error(new AccessDeniedException(
                                    "User does not have access to this business")));
                })
                .then();
    }

    private Mono<BusinessResponse> findBusiness(Long businessId) {
        return businessClient.get()
                .uri("/{id}", businessId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<BusinessResponse>>() {})
                .map(ApiResponse::getData);
    }

    private Mono<UserCredentialsResponse> currentUser(Authentication authentication) {
        return userServiceClient.findByUsername(authentication.getName());
    }

    private Mono<CreateStoreRequest> withStorageUrls(CreateStoreRequest request) {
        if (request.images() == null) {
            return Mono.just(request);
        }
        return storeImageUrlResolver.resolveRequests(request.images()).map(images -> new CreateStoreRequest(
                request.businessId(), request.name(), request.code(), request.addressLine1(), request.addressLine2(),
                request.landmark(), request.city(), request.stateProvince(), request.countryCode(), request.postalCode(),
                request.phone(), request.alternatePhone(), request.email(), request.website(), request.latitude(),
                request.longitude(), images));
    }

    private Mono<UpdateStoreRequest> withStorageUrls(UpdateStoreRequest request) {
        if (request.images() == null) {
            return Mono.just(request);
        }
        return storeImageUrlResolver.resolveRequests(request.images()).map(images -> new UpdateStoreRequest(
                request.name(), request.code(), request.addressLine1(), request.addressLine2(), request.landmark(),
                request.city(), request.stateProvince(), request.countryCode(), request.postalCode(), request.phone(),
                request.alternatePhone(), request.email(), request.website(), request.latitude(), request.longitude(),
                images, request.status()));
    }

}
