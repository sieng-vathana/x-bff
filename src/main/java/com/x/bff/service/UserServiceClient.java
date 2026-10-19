package com.x.bff.service;

import com.x.bff.dto.UserCredentialsResponse;
import com.x.bff.dto.TelegramMfaStatus;
import com.x.bff.dto.TelegramMfaLink;
import com.x.bff.dto.TelegramMfaChallenge;
import com.x.bff.dto.EmailMfaStatus;
import com.x.bff.dto.EmailMfaChallenge;
import com.sharedlib.response.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Service
public class UserServiceClient {

    private final WebClient userClient;

    public UserServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${services.user.base-url:http://localhost:8081/internal/users}") String baseUrl) {
        this.userClient = webClientBuilder.clone()
                .baseUrl(baseUrl)
                .build();
    }

    public Mono<UserCredentialsResponse> findByUsername(String username) {
        return userClient.get()
                .uri("/{username}", username)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<UserCredentialsResponse>>() {})
                .map(ApiResponse::getData);
    }

    public Mono<UserCredentialsResponse> findByUsername(String username, Long businessId) {
        return userClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/{username}")
                        .queryParam("businessId", businessId)
                        .build(username))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<UserCredentialsResponse>>() {})
                .map(ApiResponse::getData);
    }

    public Mono<TelegramMfaStatus> telegramMfaStatus(Long userId) {
        return userClient.get().uri("/{userId}/telegram-mfa", userId).retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<TelegramMfaStatus>>() {})
                .map(ApiResponse::getData);
    }

    public Mono<TelegramMfaLink> createTelegramMfaLink(Long userId) {
        return userClient.post().uri("/{userId}/telegram-mfa/link", userId).retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<TelegramMfaLink>>() {})
                .map(ApiResponse::getData);
    }

    public Mono<TelegramMfaStatus> setTelegramMfaEnabled(Long userId, boolean enabled) {
        return userClient.put().uri("/{userId}/telegram-mfa/enabled", userId)
                .bodyValue(new TelegramEnabledCommand(enabled)).retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<TelegramMfaStatus>>() {})
                .map(ApiResponse::getData);
    }

    public Mono<TelegramMfaStatus> disconnectTelegramMfa(Long userId) {
        return userClient.delete().uri("/{userId}/telegram-mfa", userId).retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<TelegramMfaStatus>>() {})
                .map(ApiResponse::getData);
    }

    public Mono<TelegramMfaChallenge> startTelegramMfaLogin(Long userId, String username) {
        return userClient.post().uri(uriBuilder -> uriBuilder.path("/{userId}/telegram-mfa/challenges")
                        .queryParam("username", username).build(userId))
                .retrieve().bodyToMono(new ParameterizedTypeReference<ApiResponse<TelegramMfaChallenge>>() {})
                .map(ApiResponse::getData);
    }

    public Mono<Boolean> verifyTelegramMfa(Long userId, String challengeToken, String code) {
        return userClient.post().uri("/telegram-mfa/verify")
                .bodyValue(new TelegramVerifyCommand(userId, challengeToken, code)).retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<Boolean>>() {})
                .map(ApiResponse::getData);
    }

    public Mono<EmailMfaStatus> emailMfaStatus(Long userId) {
        return userClient.get().uri("/{userId}/email-mfa", userId).retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<EmailMfaStatus>>() {})
                .map(ApiResponse::getData);
    }

    public Mono<EmailMfaChallenge> startEmailMfaSetup(Long userId) {
        return userClient.post().uri("/{userId}/email-mfa/verification", userId).retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<EmailMfaChallenge>>() {})
                .map(ApiResponse::getData);
    }

    public Mono<Boolean> verifyEmailMfaSetup(Long userId, String challengeToken, String code) {
        return userClient.post().uri("/{userId}/email-mfa/verification/confirm", userId)
                .bodyValue(new EmailVerifyCommand(challengeToken, code)).retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<Boolean>>() {})
                .map(ApiResponse::getData);
    }

    public Mono<EmailMfaStatus> setEmailMfaEnabled(Long userId, boolean enabled) {
        return userClient.put().uri("/{userId}/email-mfa/enabled", userId)
                .bodyValue(new EmailEnabledCommand(enabled)).retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<EmailMfaStatus>>() {})
                .map(ApiResponse::getData);
    }

    public Mono<EmailMfaStatus> disconnectEmailMfa(Long userId) {
        return userClient.delete().uri("/{userId}/email-mfa", userId).retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<EmailMfaStatus>>() {})
                .map(ApiResponse::getData);
    }

    public Mono<EmailMfaChallenge> startEmailMfaLogin(Long userId) {
        return userClient.post().uri("/{userId}/email-mfa/challenges", userId).retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<EmailMfaChallenge>>() {})
                .map(ApiResponse::getData);
    }

    public Mono<Boolean> verifyEmailMfa(Long userId, String challengeToken, String code) {
        return userClient.post().uri("/email-mfa/verify")
                .bodyValue(new EmailVerifyLoginCommand(userId, challengeToken, code)).retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<Boolean>>() {})
                .map(ApiResponse::getData);
    }

    public Mono<RegisteredUser> register(String fullName, String username, String password, String email, String phone) {
        return userClient.post()
                .bodyValue(new UserRegistrationCommand(fullName, username, password, email, phone))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<RegisteredUser>>() {})
                .map(ApiResponse::getData);
    }

    /** Removes an account whose registration failed, with the roles made for its business. */
    public Mono<Void> deleteFailedRegistration(String username, Long businessId) {
        return userClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/{username}/registration-failure")
                        .queryParamIfPresent("businessId", Optional.ofNullable(businessId))
                        .build(username))
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    public Mono<Void> assignOwnerStoreMembership(Long userId, Long storeId) {
        return userClient.post()
                .uri("/{userId}/stores/{storeId}/owner", userId, storeId)
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    public Mono<Void> ensureOwnerBusinessAccess(
            Long userId,
            Long businessId,
            List<Long> storeIds) {
        return userClient.post()
                .uri("/{userId}/businesses/{businessId}/owner-access", userId, businessId)
                .bodyValue(new OwnerAccessCommand(storeIds))
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    private record UserRegistrationCommand(String fullName, String username, String password, String email, String phone) {
    }

    private record OwnerAccessCommand(List<Long> storeIds) {
    }

    private record TelegramEnabledCommand(boolean enabled) { }
    private record TelegramVerifyCommand(Long userId, String challengeToken, String code) { }
    private record EmailEnabledCommand(boolean enabled) { }
    private record EmailVerifyCommand(String challengeToken, String code) { }
    private record EmailVerifyLoginCommand(Long userId, String challengeToken, String code) { }

    public record RegisteredUser(Long id) {
    }
}
