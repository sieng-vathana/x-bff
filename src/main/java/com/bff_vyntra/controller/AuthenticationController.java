package com.bff_vyntra.controller;

import com.bff_vyntra.dto.AuthRequest;
import com.bff_vyntra.service.ServiceClientFactory;
import com.bff_vyntra.utils.VyntraUtil;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping({"/api/v1/auth"})
@RequiredArgsConstructor
public class AuthenticationController {

    private final ServiceClientFactory clientFactory;
    private WebClient authClient;
    private WebClient userClient;

    @PostConstruct
    void init() {
        this.authClient = clientFactory.forService("/api/v1/auth");
        this.userClient = clientFactory.forService("/api/v1/users");
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<?>> login(
            @RequestBody AuthRequest request,
            @RequestHeader(value = "X-Client-Type", defaultValue = "mobile") String clientType) {
        return authClient.post()
                .uri("/login")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .map(responseStr -> {
                    ResponseEntity<?> responseEntity = VyntraUtil.toJsonResponse(responseStr);
                    if ("web".equalsIgnoreCase(clientType) && responseEntity.getBody() instanceof JsonNode) {
                        JsonNode jsonNode = (JsonNode) responseEntity.getBody();
                        if (jsonNode.has("data") && jsonNode.get("data").has("accessToken")) {
                            String token = jsonNode.get("data").get("accessToken").asText();
                            org.springframework.http.ResponseCookie cookie = org.springframework.http.ResponseCookie.from("access_token", token)
                                    .httpOnly(true)
                                    .secure(false) // Set to true in production with HTTPS
                                    .path("/")
                                    .maxAge(3600)
                                    .build();
                            return ResponseEntity.ok()
                                    .header(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString())
                                    .body(responseEntity.getBody());
                        }
                    }
                    return responseEntity;
                });
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<?>> refresh(@RequestParam String refreshToken) {
        return authClient.post()
                .uri(uriBuilder -> uriBuilder.path("/refresh").queryParam("refreshToken", refreshToken).build())
                .retrieve()
                .bodyToMono(String.class)
                .map(VyntraUtil::toJsonResponse);
    }

    @GetMapping("/user/{username}/business")
    public Mono<ResponseEntity<?>> getBusinessData(@PathVariable String username) {
        return userClient.get()
                .uri("/user/{username}/business", username)
                .retrieve()
                .bodyToMono(String.class)
                .map(VyntraUtil::toJsonResponse);
    }

    @GetMapping("/user/{username}/store")
    public Mono<ResponseEntity<?>> getStoreData(@PathVariable String username) {
        return userClient.get()
                .uri("/user/{username}/store", username)
                .retrieve()
                .bodyToMono(String.class)
                .map(VyntraUtil::toJsonResponse);
    }

    @GetMapping("/user/{username}/permissions")
    public Mono<ResponseEntity<?>> getPermissions(@PathVariable String username) {
        return userClient.get()
                .uri("/user/{username}/permissions", username)
                .retrieve()
                .bodyToMono(String.class)
                .map(VyntraUtil::toJsonResponse);
    }
}
