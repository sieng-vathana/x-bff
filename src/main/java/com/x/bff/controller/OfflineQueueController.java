package com.x.bff.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.x.bff.dto.BusinessResponse;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Cash sales held offline on each POS device. A device reports its own queue; the merchant and staff read it.
 * The business is always the one the caller belongs to.
 */
@RestController
@RequestMapping("/api/v1/businesses")
public class OfflineQueueController {

    private final WebClient paymentClient;
    private final WebClient businessClient;
    private final UserServiceClient userServiceClient;

    public OfflineQueueController(ServiceClientFactory clientFactory, UserServiceClient userServiceClient) {
        this.paymentClient = clientFactory.forService("payment", "/api/v1/payments");
        this.businessClient = clientFactory.forService("business", "/api/v1/businesses");
        this.userServiceClient = userServiceClient;
    }

    @PutMapping("/{id}/offline-queue/{deviceId}")
    @PreAuthorize("hasAuthority('x-payment:create') or hasAuthority('x-order:create')")
    public Mono<ResponseEntity<?>> report(
            @PathVariable Long id, @PathVariable String deviceId, @RequestBody JsonNode body, Authentication authentication) {
        return requireBusinessAccess(id, authentication).then(Mono.defer(() -> {
            ObjectNode report = ((ObjectNode) body).deepCopy();
            report.put("businessId", id);
            return forward(paymentClient.put().uri("/offline-queue/{deviceId}", deviceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(report));
        }));
    }

    @GetMapping("/{id}/offline-queue")
    @PreAuthorize("hasAuthority('x-report:read') or hasAuthority('x-payment:read')")
    public Mono<ResponseEntity<?>> forBusiness(@PathVariable Long id, Authentication authentication) {
        return requireBusinessAccess(id, authentication)
                .then(forward(paymentClient.get().uri(uri -> uri.path("/offline-queue").queryParam("businessId", id).build())));
    }

    @GetMapping("/offline-queue/all")
    @PreAuthorize("hasAuthority('platform:admin')")
    public Mono<ResponseEntity<?>> forEveryBusiness() {
        return forward(paymentClient.get().uri("/offline-queue"));
    }

    private Mono<Void> requireBusinessAccess(Long businessId, Authentication authentication) {
        return userServiceClient.findByUsername(authentication.getName())
                .flatMap(user -> {
                    if (user.getBusinessIds() != null && user.getBusinessIds().contains(businessId)) {
                        return Mono.empty();
                    }
                    return businessClient.get()
                            .uri("/{id}", businessId)
                            .retrieve()
                            .bodyToMono(new ParameterizedTypeReference<com.sharedlib.response.ApiResponse<BusinessResponse>>() {})
                            .map(com.sharedlib.response.ApiResponse::getData)
                            .filter(business -> Objects.equals(user.getId(), business.ownerUserId()))
                            .switchIfEmpty(Mono.error(new AccessDeniedException("User does not have access to this business")))
                            .then();
                });
    }

    private Mono<ResponseEntity<?>> forward(WebClient.RequestHeadersSpec<?> request) {
        return request.exchangeToMono(response -> response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> XUtil.toJsonResponse(body, response.statusCode())));
    }
}
