package com.bff_vyntra.controller;

import com.bff_vyntra.dto.AuthRequest;
import com.bff_vyntra.dto.AuthResponse;
import com.bff_vyntra.dto.RefreshTokenRequest;
import com.bff_vyntra.security.AuthCookieSupport;
import com.bff_vyntra.security.ClientChannel;
import com.bff_vyntra.security.JwtUtils;
import com.bff_vyntra.service.AuthenticationService;
import com.sharedlib.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final JwtUtils jwtUtils;

    public AuthenticationController(AuthenticationService authenticationService, JwtUtils jwtUtils) {
        this.authenticationService = authenticationService;
        this.jwtUtils = jwtUtils;
    }

    /**
     * Dual-channel login.
     * <ul>
     *   <li>mobile (default): tokens returned in JSON body; no auth cookies</li>
     *   <li>web: HttpOnly access + refresh cookies; tokens stripped from JSON body</li>
     * </ul>
     */
    @PostMapping("/login")
    public Mono<ResponseEntity<ApiResponse<AuthResponse>>> login(
            @Valid @RequestBody AuthRequest request,
            @RequestHeader(value = ClientChannel.HEADER_NAME, defaultValue = "mobile") String clientType,
            ServerWebExchange exchange) {
        ClientChannel channel = ClientChannel.fromHeader(clientType);
        return authenticationService.authenticate(request, channel)
                .map(response -> buildAuthResponse(response, channel, exchange));
    }

    /**
     * Dual-channel refresh.
     * Mobile: send {@code refreshToken} in JSON body.
     * Web: omit body (or leave empty) and send the {@code refresh_token} cookie.
     * Query-string refresh tokens are no longer accepted.
     */
    @PostMapping("/refresh")
    public Mono<ResponseEntity<ApiResponse<AuthResponse>>> refresh(
            @RequestBody(required = false) RefreshTokenRequest body,
            @RequestHeader(value = ClientChannel.HEADER_NAME, defaultValue = "mobile") String clientType,
            ServerWebExchange exchange) {
        ClientChannel channel = ClientChannel.fromHeader(clientType);
        String refreshToken = resolveRefreshToken(body, exchange.getRequest());
        return authenticationService.refresh(refreshToken, channel)
                .map(response -> buildAuthResponse(response, channel, exchange));
    }

    /**
     * Clears web auth cookies. Mobile clients should discard local tokens after calling this.
     */
    @PostMapping("/logout")
    public Mono<ResponseEntity<ApiResponse<Void>>> logout(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        response.addCookie(AuthCookieSupport.clearAccessCookie(request));
        response.addCookie(AuthCookieSupport.clearRefreshCookie(request));
        return Mono.just(ResponseEntity.ok(ApiResponse.success(200, "Logged out", null)));
    }

    private String resolveRefreshToken(RefreshTokenRequest body, ServerHttpRequest request) {
        if (body != null && StringUtils.hasText(body.getRefreshToken())) {
            return body.getRefreshToken().trim();
        }
        org.springframework.http.HttpCookie cookie =
                request.getCookies().getFirst(AuthCookieSupport.REFRESH_COOKIE);
        if (cookie != null && StringUtils.hasText(cookie.getValue())) {
            return cookie.getValue();
        }
        return null;
    }

    private ResponseEntity<ApiResponse<AuthResponse>> buildAuthResponse(
            ApiResponse<AuthResponse> response,
            ClientChannel channel,
            ServerWebExchange exchange) {
        AuthResponse data = response.getData();
        if (data == null || !channel.isWeb()) {
            return ResponseEntity.ok(response);
        }

        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse httpResponse = exchange.getResponse();

        String accessToken = data.getAccessToken();
        String refreshToken = data.getRefreshToken();
        long accessMaxAge = data.getExpiresIn() != null
                ? data.getExpiresIn()
                : jwtUtils.getAccessExpirationSeconds();
        long refreshMaxAge = jwtUtils.getRefreshExpirationSeconds();

        if (StringUtils.hasText(accessToken)) {
            httpResponse.addCookie(AuthCookieSupport.accessCookie(accessToken, accessMaxAge, request));
        }
        if (StringUtils.hasText(refreshToken)) {
            httpResponse.addCookie(AuthCookieSupport.refreshCookie(refreshToken, refreshMaxAge, request));
        }

        AuthResponse webSafe = AuthResponse.builder()
                .accessToken(null)
                .refreshToken(null)
                .expiresIn(data.getExpiresIn())
                .tokenType(data.getTokenType())
                .channel(ClientChannel.WEB.wireValue())
                .user(data.getUser())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response.getCode(), webSafe));
    }
}
