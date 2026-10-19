package com.x.bff.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sharedlib.response.ApiResponse;
import com.x.bff.dto.UserCredentialsResponse;
import com.x.bff.service.ServiceClientFactory;
import com.x.bff.service.UserServiceClient;
import com.x.bff.utils.XUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.HtmlUtils;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Platform backoffice: lists every merchant and store and suspends or reinstates them with a reason.
 * The merchant hears about it on its Telegram chats and by email to the owner.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('platform:admin')")
public class SuspensionController {
    private static final Logger log = LoggerFactory.getLogger(SuspensionController.class);
    private static final ParameterizedTypeReference<ApiResponse<JsonNode>> JSON = new ParameterizedTypeReference<>() {};

    private final WebClient businessClient;
    private final WebClient storeClient;
    private final WebClient telegramClient;
    private final WebClient userNoticeClient;
    private final UserServiceClient userServiceClient;

    public SuspensionController(ServiceClientFactory clientFactory, UserServiceClient userServiceClient,
                                WebClient.Builder webClientBuilder,
                                @org.springframework.beans.factory.annotation.Value("${services.user.base-url:http://localhost:8081/internal/users}") String userBaseUrl) {
        this.businessClient = clientFactory.forService("business", "/api/v1/businesses");
        this.storeClient = clientFactory.forService("store", "/api/v1/stores");
        this.telegramClient = clientFactory.forService("business", "/internal/businesses");
        this.userNoticeClient = webClientBuilder.clone().baseUrl(userBaseUrl).build();
        this.userServiceClient = userServiceClient;
    }

    @GetMapping("/merchants")
    public Mono<ResponseEntity<?>> merchants(@RequestParam(required = false) String status,
                                             @RequestParam(required = false) String search) {
        return forward(businessClient.get().uri(uri -> uri.path("/admin")
                .queryParamIfPresent("status", java.util.Optional.ofNullable(status))
                .queryParamIfPresent("search", java.util.Optional.ofNullable(search)).build()));
    }

    @PostMapping("/merchants/{id}/suspension")
    public Mono<ResponseEntity<?>> suspendMerchant(@PathVariable Long id, @Valid @RequestBody SuspendRequest request,
                                                   Authentication authentication) {
        return actor(authentication).flatMap(actor -> businessClient.post()
                        .uri(uri -> uri.path("/{id}/suspension").queryParam("actorUserId", actor.getId()).build(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("reason", request.reason()))
                        .exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty("")
                                .flatMap(body -> {
                                    ResponseEntity<?> result = XUtil.toJsonResponse(body, response.statusCode());
                                    if (!response.statusCode().is2xxSuccessful()) return Mono.just(result);
                                    return business(id).flatMap(business -> notifyMerchant(business, null,
                                                    merchantSuspendedTelegram(business, request.reason()),
                                                    "Your merchant account has been suspended", "Merchant account suspended",
                                                    merchantSuspendedEmail(business, request.reason())))
                                            .thenReturn(result);
                                })));
    }

    @DeleteMapping("/merchants/{id}/suspension")
    public Mono<ResponseEntity<?>> reinstateMerchant(@PathVariable Long id) {
        return businessClient.delete().uri("/{id}/suspension", id)
                .exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty("")
                        .flatMap(body -> {
                            ResponseEntity<?> result = XUtil.toJsonResponse(body, response.statusCode());
                            if (!response.statusCode().is2xxSuccessful()) return Mono.just(result);
                            return business(id).flatMap(business -> notifyMerchant(business, null,
                                            "✅ <b>TinhLuk account reinstated</b>\n<b>" + esc(name(business)) + "</b> is active again. "
                                                    + "Sign-in, sales and marketplace listings are back on.",
                                            "Your merchant account has been reinstated", "Merchant account reinstated",
                                            name(business) + " has been reinstated. You can sign in, sell and appear on the "
                                                    + "TinhLuk marketplace again."))
                                    .thenReturn(result);
                        }));
    }

    @GetMapping("/stores")
    public Mono<ResponseEntity<?>> stores(@RequestParam(required = false) Long businessId,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) String search) {
        return forward(storeClient.get().uri(uri -> uri.path("/admin")
                .queryParamIfPresent("businessId", java.util.Optional.ofNullable(businessId))
                .queryParamIfPresent("status", java.util.Optional.ofNullable(status))
                .queryParamIfPresent("search", java.util.Optional.ofNullable(search)).build()));
    }

    @PostMapping("/stores/{id}/suspension")
    public Mono<ResponseEntity<?>> suspendStore(@PathVariable Long id, @Valid @RequestBody SuspendRequest request,
                                                Authentication authentication) {
        return actor(authentication).flatMap(actor -> storeClient.post()
                .uri(uri -> uri.path("/{id}/suspension").queryParam("actorUserId", actor.getId()).build(id))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("reason", request.reason()))
                .exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty("")
                        .flatMap(body -> {
                            ResponseEntity<?> result = XUtil.toJsonResponse(body, response.statusCode());
                            if (!response.statusCode().is2xxSuccessful()) return Mono.just(result);
                            return store(id).flatMap(store -> business(store.path("businessId").asLong())
                                            .flatMap(business -> notifyMerchant(business, id,
                                                    "⛔ <b>Store suspended by TinhLuk</b>\n<b>" + esc(store.path("name").asText()) + "</b> ("
                                                            + esc(name(business)) + ")\n\n<b>Reason:</b> " + esc(request.reason())
                                                            + "\n\nSales and the marketplace listing for this store are paused. "
                                                            + "Contact TinhLuk support if you think this is a mistake.",
                                                    "Your store " + store.path("name").asText() + " has been suspended", "Store suspended",
                                                    "Your store " + store.path("name").asText() + " (" + name(business)
                                                            + ") has been suspended by TinhLuk.\n\nReason: " + request.reason()
                                                            + "\n\nSales and the marketplace listing for this store are paused until it is "
                                                            + "reinstated. Your other stores are not affected. If you think this is a "
                                                            + "mistake, please contact TinhLuk support.")))
                                    .thenReturn(result);
                        })));
    }

    @DeleteMapping("/stores/{id}/suspension")
    public Mono<ResponseEntity<?>> reinstateStore(@PathVariable Long id) {
        return storeClient.delete().uri("/{id}/suspension", id)
                .exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty("")
                        .flatMap(body -> {
                            ResponseEntity<?> result = XUtil.toJsonResponse(body, response.statusCode());
                            if (!response.statusCode().is2xxSuccessful()) return Mono.just(result);
                            return store(id).flatMap(store -> business(store.path("businessId").asLong())
                                            .flatMap(business -> notifyMerchant(business, id,
                                                    "✅ <b>Store reinstated</b>\n<b>" + esc(store.path("name").asText())
                                                            + "</b> can sell and appear on the marketplace again.",
                                                    "Your store " + store.path("name").asText() + " has been reinstated", "Store reinstated",
                                                    "Your store " + store.path("name").asText() + " (" + name(business)
                                                            + ") has been reinstated. It can sell and appear on the TinhLuk marketplace again.")))
                                    .thenReturn(result);
                        }));
    }

    /**
     * Telegram to the merchant's chats (those covering the store, when one is given) and an email to the owner.
     * Both are best effort: the suspension already happened, so a failed notice is only logged.
     */
    private Mono<Void> notifyMerchant(JsonNode business, Long storeId, String telegramHtml,
                                      String emailSubject, String emailTitle, String emailMessage) {
        long businessId = business.path("id").asLong();
        Map<String, Object> alert = new java.util.HashMap<>(Map.of("type", "ACCOUNT", "text", telegramHtml, "parseMode", "HTML"));
        if (storeId != null) alert.put("storeId", storeId);
        Mono<Void> telegram = telegramClient.post().uri("/{id}/telegram/notify", businessId)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(alert)
                .retrieve().toBodilessEntity().then()
                .onErrorResume(error -> {
                    log.warn("Suspension Telegram notice for business {} failed: {}", businessId, error.getMessage());
                    return Mono.empty();
                });
        long ownerId = business.path("ownerUserId").asLong(0);
        Mono<Void> email = ownerId <= 0 ? Mono.empty() : userNoticeClient.post().uri("/{id}/notices", ownerId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("subject", emailSubject, "title", emailTitle, "message", emailMessage))
                .retrieve().toBodilessEntity().then()
                .onErrorResume(error -> {
                    log.warn("Suspension email notice for business {} failed: {}", businessId, error.getMessage());
                    return Mono.empty();
                });
        return Mono.when(telegram, email);
    }

    private static String merchantSuspendedTelegram(JsonNode business, String reason) {
        return "⛔ <b>TinhLuk account suspended</b>\n<b>" + esc(name(business)) + "</b> has been suspended by TinhLuk.\n\n"
                + "<b>Reason:</b> " + esc(reason) + "\n\n"
                + "Sign-in, sales and marketplace listings are paused. Contact TinhLuk support if you think this is a mistake.";
    }

    private static String merchantSuspendedEmail(JsonNode business, String reason) {
        return name(business) + " has been suspended by TinhLuk.\n\nReason: " + reason
                + "\n\nWhile the account is suspended, you and your staff cannot sign in to the merchant portal, "
                + "new sales and KHQR payments are blocked, and your stores are hidden from the TinhLuk marketplace. "
                + "Your existing records are kept.\n\nIf you think this is a mistake, please contact TinhLuk support.";
    }

    private Mono<JsonNode> business(long id) {
        return businessClient.get().uri("/{id}", id).retrieve().bodyToMono(JSON).mapNotNull(ApiResponse::getData);
    }

    private Mono<JsonNode> store(long id) {
        return storeClient.get().uri("/{id}", id).retrieve().bodyToMono(JSON).mapNotNull(ApiResponse::getData);
    }

    private Mono<UserCredentialsResponse> actor(Authentication authentication) {
        return userServiceClient.findByUsername(authentication.getName());
    }

    private Mono<ResponseEntity<?>> forward(WebClient.RequestHeadersSpec<?> request) {
        return request.exchangeToMono(response -> response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> XUtil.toJsonResponse(body, response.statusCode())));
    }

    private static String name(JsonNode business) {
        return business.path("name").asText("Your merchant");
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value.trim());
    }

    public record SuspendRequest(@NotBlank @Size(max = 500) String reason) {
    }
}
