package com.x.bff.controller;

import com.x.bff.dto.AuthRequest;
import com.x.bff.dto.AuthResponse;
import com.x.bff.dto.AuthUserSummary;
import com.x.bff.dto.RefreshTokenRequest;
import com.x.bff.security.JwtUtils;
import com.x.bff.security.SecurityConfig;
import com.x.bff.service.AuthenticationService;
import com.sharedlib.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(AuthenticationController.class)
@Import(SecurityConfig.class)
class AuthenticationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AuthenticationService authenticationService;

    @MockBean
    private JwtUtils jwtUtils;

    @Test
    void loginForMobileReturnsTokensWithoutCookies() {
        AuthResponse response = sampleAuthResponse("test-access-token", "test-refresh-token", "mobile");

        when(authenticationService.authenticate(any(AuthRequest.class), any()))
                .thenReturn(Mono.just(ApiResponse.success(200, response)));

        webTestClient.post()
                .uri("/api/v1/auth/login")
                .header("X-Client-Type", "mobile")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AuthRequest("user", "pass"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist("Set-Cookie")
                .expectBody()
                .jsonPath("$.data.accessToken").isEqualTo("test-access-token")
                .jsonPath("$.data.refreshToken").isEqualTo("test-refresh-token")
                .jsonPath("$.data.channel").isEqualTo("mobile")
                .jsonPath("$.data.tokenType").isEqualTo("Bearer")
                .jsonPath("$.data.user.username").isEqualTo("user");
    }

    @Test
    void loginForWebSetsCookiesAndOmitsTokensFromBody() {
        AuthResponse response = sampleAuthResponse("test-access-token", "test-refresh-token", "web");

        when(authenticationService.authenticate(any(AuthRequest.class), any()))
                .thenReturn(Mono.just(ApiResponse.success(200, response)));
        when(jwtUtils.getRefreshExpirationSeconds()).thenReturn(604800L);

        webTestClient.post()
                .uri("/api/v1/auth/login")
                .header("X-Client-Type", "web")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AuthRequest("user", "pass"))
                .exchange()
                .expectStatus().isOk()
                .expectCookie().valueEquals("access_token", "test-access-token")
                .expectCookie().httpOnly("access_token", true)
                .expectCookie().valueEquals("refresh_token", "test-refresh-token")
                .expectCookie().httpOnly("refresh_token", true)
                .expectBody()
                .jsonPath("$.data.accessToken").doesNotExist()
                .jsonPath("$.data.refreshToken").doesNotExist()
                .jsonPath("$.data.channel").isEqualTo("web")
                .jsonPath("$.data.user.username").isEqualTo("user");
    }

    @Test
    void refreshFromBodyWorksForMobile() {
        AuthResponse response = sampleAuthResponse("new-access", "new-refresh", "mobile");

        when(authenticationService.refresh(eq("old-refresh"), any()))
                .thenReturn(Mono.just(ApiResponse.success(200, response)));

        webTestClient.post()
                .uri("/api/v1/auth/refresh")
                .header("X-Client-Type", "mobile")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RefreshTokenRequest("old-refresh"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist("Set-Cookie")
                .expectBody()
                .jsonPath("$.data.accessToken").isEqualTo("new-access");
    }

    @Test
    void refreshFromCookieWorksForWeb() {
        AuthResponse response = sampleAuthResponse("new-access", "new-refresh", "web");

        when(authenticationService.refresh(eq("cookie-refresh"), any()))
                .thenReturn(Mono.just(ApiResponse.success(200, response)));
        when(jwtUtils.getRefreshExpirationSeconds()).thenReturn(604800L);

        webTestClient.post()
                .uri("/api/v1/auth/refresh")
                .header("X-Client-Type", "web")
                .cookie("refresh_token", "cookie-refresh")
                .exchange()
                .expectStatus().isOk()
                .expectCookie().valueEquals("access_token", "new-access")
                .expectCookie().valueEquals("refresh_token", "new-refresh")
                .expectBody()
                .jsonPath("$.data.accessToken").doesNotExist();
    }

    @Test
    void logoutClearsAuthCookies() {
        webTestClient.post()
                .uri("/api/v1/auth/logout")
                .exchange()
                .expectStatus().isOk()
                .expectCookie().maxAge("access_token", java.time.Duration.ZERO)
                .expectCookie().maxAge("refresh_token", java.time.Duration.ZERO);
    }

    @Test
    void blankLoginIsRejected() {
        webTestClient.post()
                .uri("/api/v1/auth/login")
                .header("X-Client-Type", "mobile")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AuthRequest("", ""))
                .exchange()
                .expectStatus().isBadRequest();
    }

    private static AuthResponse sampleAuthResponse(String access, String refresh, String channel) {
        return AuthResponse.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .expiresIn(900L)
                .tokenType("Bearer")
                .channel(channel)
                .user(AuthUserSummary.builder()
                        .id(1L)
                        .username("user")
                        .permissions(Set.of("VIEW_PRODUCTS"))
                        .build())
                .build();
    }
}
