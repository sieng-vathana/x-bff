package com.x.bff.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sharedlib.response.ApiResponse;
import com.x.bff.dto.BusinessResponse;
import com.x.bff.dto.UserCredentialsResponse;
import com.x.bff.service.ServiceClientFactory;
import com.x.bff.service.UserServiceClient;
import com.x.bff.utils.XUtil;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Objects;

/** The business's loyalty points rules and member discount. */
@RestController
@RequestMapping("/api/v1/loyalty")
public class LoyaltyController {

    private final WebClient loyaltyClient;
    private final WebClient businessClient;
    private final UserServiceClient userServiceClient;

    public LoyaltyController(ServiceClientFactory clientFactory, UserServiceClient userServiceClient) {
        this.loyaltyClient = clientFactory.forService("customer", "/api/v1/loyalty");
        this.businessClient = clientFactory.forService("business", "/api/v1/businesses");
        this.userServiceClient = userServiceClient;
    }

    /** Read by the POS too, for member prices and redeeming points. */
    @GetMapping("/program")
    @PreAuthorize("hasAuthority('x-customer:read')")
    public Mono<ResponseEntity<?>> program(@RequestParam Long businessId, Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(forward(loyaltyClient.get().uri(uri -> uri
                        .path("/program")
                        .queryParam("businessId", businessId)
                        .build())));
    }

    @PutMapping("/program")
    @PreAuthorize("hasAuthority('x-business:update')")
    public Mono<ResponseEntity<?>> updateProgram(
            @RequestParam Long businessId,
            @RequestBody JsonNode request,
            Authentication authentication) {
        return requireBusinessAccess(businessId, authentication)
                .then(forward(loyaltyClient.put()
                        .uri(uri -> uri.path("/program").queryParam("businessId", businessId).build())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request)));
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
