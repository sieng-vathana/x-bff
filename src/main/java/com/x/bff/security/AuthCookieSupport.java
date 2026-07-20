package com.x.bff.security;

import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.time.Duration;

/**
 * Builds and clears dual-channel auth cookies for the web client.
 */
public final class AuthCookieSupport {

    public static final String ACCESS_COOKIE = "access_token";
    public static final String REFRESH_COOKIE = "refresh_token";

    private AuthCookieSupport() {
    }

    public static ResponseCookie accessCookie(String token, long maxAgeSeconds, ServerHttpRequest request) {
        return baseCookie(ACCESS_COOKIE, token, maxAgeSeconds, "/", request).build();
    }

    public static ResponseCookie refreshCookie(String token, long maxAgeSeconds, ServerHttpRequest request) {
        // Restrict refresh cookie to auth endpoints only.
        return baseCookie(REFRESH_COOKIE, token, maxAgeSeconds, "/api/v1/auth", request).build();
    }

    public static ResponseCookie clearAccessCookie(ServerHttpRequest request) {
        return baseCookie(ACCESS_COOKIE, "", 0, "/", request).build();
    }

    public static ResponseCookie clearRefreshCookie(ServerHttpRequest request) {
        return baseCookie(REFRESH_COOKIE, "", 0, "/api/v1/auth", request).build();
    }

    private static ResponseCookie.ResponseCookieBuilder baseCookie(
            String name,
            String value,
            long maxAgeSeconds,
            String path,
            ServerHttpRequest request) {
        return ResponseCookie.from(name, value == null ? "" : value)
                .httpOnly(true)
                .secure(isHttps(request))
                .sameSite("Lax")
                .path(path)
                .maxAge(Duration.ofSeconds(Math.max(maxAgeSeconds, 0)));
    }

    static boolean isHttps(ServerHttpRequest request) {
        return request.getSslInfo() != null
                || "https".equalsIgnoreCase(request.getHeaders().getFirst("X-Forwarded-Proto"));
    }
}
