package com.x.bff.service;

import com.x.bff.dto.UserCredentialsResponse;
import com.sharedlib.response.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

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

    public Mono<RegisteredUser> register(String fullName, String username, String password, String email, String phone) {
        return userClient.post()
                .bodyValue(new UserRegistrationCommand(fullName, username, password, email, phone))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<RegisteredUser>>() {})
                .map(ApiResponse::getData);
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

    public record RegisteredUser(Long id) {
    }
}
