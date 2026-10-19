package com.x.bff.controller;

import com.x.bff.dto.BusinessResponse;
import com.sharedlib.response.PageResponse;
import com.x.bff.dto.StoreResponse;
import com.x.bff.dto.CreateBusinessRequest;
import com.x.bff.dto.UserCredentialsResponse;
import com.x.bff.service.ServiceClientFactory;
import com.x.bff.service.UserServiceClient;
import com.x.bff.utils.XUtil;
import com.sharedlib.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/businesses")
public class BusinessController {

    private final WebClient businessClient;
    private final UserServiceClient userServiceClient;
    private final WebClient storeClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .findAndRegisterModules()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    @org.springframework.beans.factory.annotation.Value("${app.portal-url:https://portal.learner-teach.online}")
    private String portalUrl;

    public BusinessController(ServiceClientFactory clientFactory, UserServiceClient userServiceClient) {
        this.businessClient = clientFactory.forService("business", "/api/v1/businesses");
        this.storeClient = clientFactory.forService("store", "/api/v1/stores");
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
                                request.fiscalYearStartMonth(),
                                request.usdToKhrExchangeRate()))
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

    @GetMapping("/{id}/exchange-rate-reference")
    @PreAuthorize("hasAuthority('x-business:read')")
    public Mono<ResponseEntity<?>> exchangeRateReference(@PathVariable Long id, Authentication authentication) {
        return requireBusinessAccess(id, authentication)
                .then(forward(businessClient.get().uri("/{id}/exchange-rate-reference", id)));
    }

    @PostMapping("/{id}/exchange-rate-reference/refresh")
    @PreAuthorize("hasAuthority('x-business:read')")
    public Mono<ResponseEntity<?>> refreshExchangeRateReference(@PathVariable Long id, Authentication authentication) {
        return requireBusinessAccess(id, authentication)
                .then(forward(businessClient.post().uri("/{id}/exchange-rate-reference/refresh", id)));
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

    @GetMapping("/approvals/pending")
    @PreAuthorize("hasAuthority('platform:admin')")
    public Mono<ResponseEntity<?>> pendingApprovals() {
        return forward(businessClient.get().uri("/approvals/pending"));
    }

    @PostMapping("/{id}/approval/approve")
    @PreAuthorize("hasAuthority('platform:admin')")
    public Mono<ResponseEntity<?>> approve(@PathVariable Long id, Authentication authentication) {
        return currentUser(authentication).flatMap(user -> forwardThen(businessClient.post()
                .uri(uri -> uri.path("/{id}/approval/approve")
                        .queryParam("reviewerUserId", user.getId()).build(id)), business -> tellApproved(business)));
    }

    @PostMapping("/{id}/approval/reject")
    @PreAuthorize("hasAuthority('platform:admin')")
    public Mono<ResponseEntity<?>> reject(@PathVariable Long id,
            @Valid @RequestBody ReviewBusinessRequest request, Authentication authentication) {
        return currentUser(authentication).flatMap(user -> forwardThen(businessClient.post()
                .uri(uri -> uri.path("/{id}/approval/reject")
                        .queryParam("reviewerUserId", user.getId()).build(id))
                .bodyValue(request), business -> tellNeedsChanges(business, request.reason())));
    }

    @PostMapping("/{id}/approval/resubmit")
    @PreAuthorize("hasAuthority('x-business:update')")
    public Mono<ResponseEntity<?>> resubmit(@PathVariable Long id, Authentication authentication) {
        return requireBusinessAccess(id, authentication)
                .then(forward(businessClient.post().uri("/{id}/approval/resubmit", id)));
    }

    @GetMapping("/{id}/telegram")
    @PreAuthorize("hasAuthority('x-business:read')")
    public Mono<ResponseEntity<?>> telegramStatus(@PathVariable Long id, Authentication authentication) {
        return requireBusinessAccess(id, authentication)
                .then(forward(businessClient.get().uri("/{id}/telegram", id)));
    }

    @PutMapping("/{id}/telegram")
    @PreAuthorize("hasAuthority('x-business:update')")
    public Mono<ResponseEntity<?>> updateTelegramAlerts(
            @PathVariable Long id,
            @Valid @RequestBody TelegramAlertsRequest request,
            Authentication authentication) {
        return requireBusinessAccess(id, authentication)
                .then(forward(businessClient.put()
                        .uri("/{id}/telegram", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request)));
    }

    @DeleteMapping("/{id}/telegram")
    @PreAuthorize("hasAuthority('x-business:update')")
    public Mono<ResponseEntity<?>> disconnectTelegram(@PathVariable Long id, Authentication authentication) {
        return requireBusinessAccess(id, authentication)
                .then(forward(businessClient.delete().uri("/{id}/telegram", id)));
    }

    /** One-time t.me links that connect a Telegram chat when someone presses Start. */
    @PostMapping("/{id}/telegram/link")
    @PreAuthorize("hasAuthority('x-business:update')")
    public Mono<ResponseEntity<?>> createTelegramLink(@PathVariable Long id, Authentication authentication) {
        return requireBusinessAccess(id, authentication)
                .then(forward(businessClient.post().uri("/{id}/telegram/link", id)));
    }

    @PostMapping("/{id}/telegram/test")
    @PreAuthorize("hasAuthority('x-business:update')")
    public Mono<ResponseEntity<?>> sendTelegramTest(@PathVariable Long id, Authentication authentication) {
        return requireBusinessAccess(id, authentication)
                .then(forward(businessClient.post().uri("/{id}/telegram/test", id)));
    }

    /** Which stores and alerts one connected chat gets. */
    @PutMapping("/{id}/telegram/chats/{chatId}")
    @PreAuthorize("hasAuthority('x-business:update')")
    public Mono<ResponseEntity<?>> updateTelegramChat(
            @PathVariable Long id,
            @PathVariable Long chatId,
            @Valid @RequestBody TelegramChatRequest request,
            Authentication authentication) {
        return requireBusinessAccess(id, authentication)
                .then(forward(businessClient.put()
                        .uri("/{id}/telegram/chats/{chatId}", id, chatId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request)));
    }

    @DeleteMapping("/{id}/telegram/chats/{chatId}")
    @PreAuthorize("hasAuthority('x-business:update')")
    public Mono<ResponseEntity<?>> disconnectTelegramChat(@PathVariable Long id, @PathVariable Long chatId, Authentication authentication) {
        return requireBusinessAccess(id, authentication)
                .then(forward(businessClient.delete().uri("/{id}/telegram/chats/{chatId}", id, chatId)));
    }

    @PostMapping("/{id}/telegram/chats/{chatId}/test")
    @PreAuthorize("hasAuthority('x-business:update')")
    public Mono<ResponseEntity<?>> sendTelegramChatTest(@PathVariable Long id, @PathVariable Long chatId, Authentication authentication) {
        return requireBusinessAccess(id, authentication)
                .then(forward(businessClient.post().uri("/{id}/telegram/chats/{chatId}/test", id, chatId)));
    }

/**
     * Forwards the call and, when it succeeds, runs then with the business it returned. The notice is best effort and
     * never changes the answer the backoffice gets.
     */
    private Mono<ResponseEntity<?>> forwardThen(WebClient.RequestHeadersSpec<?> request,
                                                java.util.function.Function<BusinessResponse, Mono<Void>> then) {
        return request.exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty("")
                .flatMap(body -> {
                    ResponseEntity<?> result = XUtil.toJsonResponse(body, response.statusCode());
                    if (!response.statusCode().is2xxSuccessful()) return Mono.just(result);
                    BusinessResponse business;
                    try {
                        business = objectMapper.readValue(body,
                                new com.fasterxml.jackson.core.type.TypeReference<ApiResponse<BusinessResponse>>() {}).getData();
                    } catch (Exception exception) {
                        return Mono.just(result);
                    }
                    return business == null ? Mono.just(result)
                            : then.apply(business).onErrorResume(error -> Mono.empty()).thenReturn(result);
                }));
    }

    /** Emails the owner that the merchant and its shops are live on TinhLuk, with where to sign in. */
    private Mono<Void> tellApproved(BusinessResponse business) {
        return shopNames(business.id()).flatMap(shops -> userServiceClient.sendNotice(business.ownerUserId(),
                "Your shop has been approved on TinhLuk",
                "Welcome to TinhLuk",
                "Good news! Your merchant application for " + business.name() + " has been approved, and "
                        + (shops.isEmpty() ? "your shop is" : shops + (shops.contains(", ") ? " are" : " is"))
                        + " now live on the TinhLuk platform.\n\n"
                        + "Sign in to the merchant portal with the username and password you registered: "
                        + portalUrl + "/sign-in\n\n"
                        + "From there you can add products, start selling at the POS with KHQR payments, and connect "
                        + "Telegram for sales alerts."));
    }

    private Mono<Void> tellNeedsChanges(BusinessResponse business, String reason) {
        return userServiceClient.sendNotice(business.ownerUserId(),
                "Your merchant application needs changes",
                "Application needs changes",
                "Thank you for applying to sell on TinhLuk. We could not approve " + business.name() + " yet.\n\n"
                        + "Reason: " + reason + "\n\n"
                        + "Sign in at " + portalUrl + "/sign-in to update your details, then submit the application again. "
                        + "We will review it as soon as you resubmit.");
    }

    private Mono<String> shopNames(Long businessId) {
        return storeClient.get()
                .uri(uri -> uri.queryParam("businessId", businessId).queryParam("page", 0).queryParam("size", 20).build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<PageResponse<StoreResponse>>>() {})
                .map(page -> page.getData() == null || page.getData().content() == null ? ""
                        : String.join(", ", page.getData().content().stream().map(StoreResponse::name).toList()))
                .onErrorReturn("")
                .defaultIfEmpty("");
    }

        private Mono<ResponseEntity<?>> forward(WebClient.RequestHeadersSpec<?> request) {
        return request.exchangeToMono(response -> response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> XUtil.toJsonResponse(body, response.statusCode())));
    }

    private record ReviewBusinessRequest(
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max = 1000) String reason) {}

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
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 160) String name,
            @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z]{3}") String defaultCurrencyCode,
            @jakarta.validation.constraints.Positive BigDecimal usdToKhrExchangeRate,
            Boolean pricesIncludeTax,
            @jakarta.validation.constraints.Size(
                    max = 100,
                    message = "Tax registration number must not exceed 100 characters")
            String taxRegistrationNumber,
            @jakarta.validation.constraints.Size(
                    max = 32,
                    message = "Tax registration label must not exceed 32 characters")
            String taxRegistrationLabel,
            @jakarta.validation.constraints.Size(max = 32)
            @jakarta.validation.constraints.Pattern(
                    regexp = "|[A-Za-z0-9._-]+@[A-Za-z0-9._-]+",
                    message = "Bakong account ID must look like name@bank")
            String bakongAccountId) {
    }

    private record TelegramAlertsRequest(
            @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(100000) Integer lowStockThreshold,
            @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(23) Integer dailySummaryHour) {
    }

    private record TelegramChatRequest(
            Boolean allStores,
            @jakarta.validation.constraints.Size(max = 200) java.util.List<@jakarta.validation.constraints.Positive Long> storeIds,
            Boolean khqrAlerts,
            Boolean orderAlerts,
            Boolean lowStockAlerts,
            Boolean dailySummary) {
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
            Integer fiscalYearStartMonth,
            BigDecimal usdToKhrExchangeRate) {
    }
}
