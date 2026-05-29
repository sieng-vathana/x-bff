package com.bff_vyntra.controller;

import com.bff_vyntra.dto.AuthRequest;
import com.bff_vyntra.dto.AuthResponse;
import com.bff_vyntra.security.JwtUtils;
import com.bff_vyntra.service.AuthenticationService;
import com.bff_vyntra.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest({AuthenticationController.class, ProductController.class})
@Import(SecurityConfig.class)
class AuthenticationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AuthenticationService authenticationService;

    @MockBean
    private JwtUtils jwtUtils;

    @Test
    void loginForMobileReturnsNoCookie() {
        AuthResponse response = AuthResponse.builder()
                .accessToken("test-access-token")
                .refreshToken("test-refresh-token")
                .build();

        when(authenticationService.authenticate(any(AuthRequest.class)))
                .thenReturn(Mono.just(com.sharedlib.response.ApiResponse.success(200, response)));

        webTestClient.post()
                .uri("/api/v1/auth/login") // Fixed URI to match controller
                .header("X-Client-Type", "mobile")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AuthRequest("user", "pass"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist("Set-Cookie")
                .expectBody()
                .jsonPath("$.data.accessToken").isEqualTo("test-access-token");
    }

    @Test
    void loginForWebReturnsCookie() {
        AuthResponse response = AuthResponse.builder()
                .accessToken("test-access-token")
                .refreshToken("test-refresh-token")
                .build();

        when(authenticationService.authenticate(any(AuthRequest.class)))
                .thenReturn(Mono.just(com.sharedlib.response.ApiResponse.success(200, response)));

        webTestClient.post()
                .uri("/api/v1/auth/login") // Fixed URI to match controller
                .header("X-Client-Type", "web")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AuthRequest("user", "pass"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueMatches("Set-Cookie", "access_token=test-access-token;.*")
                .expectBody()
                .jsonPath("$.data.accessToken").isEqualTo("test-access-token");
    }

    @Test
    void accessProtectedResourceWithCookie() {
        String token = "valid-token";
        when(jwtUtils.extractUsername(token)).thenReturn("user");
        when(jwtUtils.extractPermissions(token)).thenReturn(java.util.List.of("VIEW_PRODUCTS"));
        when(jwtUtils.validateToken(token, "user")).thenReturn(true);

        webTestClient.get()
                .uri("/api/v1/products")
                .cookie("access_token", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("Product List (Secured by @PreAuthorize)");
    }
}
