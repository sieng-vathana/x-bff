package com.x.bff.service;

import com.x.bff.dto.UserCredentialsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
                .bodyToMono(UserCredentialsResponse.class);
    }
}
