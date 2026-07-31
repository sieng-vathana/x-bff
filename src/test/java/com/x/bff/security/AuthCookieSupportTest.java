package com.x.bff.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthCookieSupportTest {

    @Test
    void usesCrossSiteSecureCookiesForHttpsRequests() {
        var request = MockServerHttpRequest.get("https://public.example/api/v1/auth/login").build();

        var cookie = AuthCookieSupport.accessCookie("token", 60, request);

        assertEquals("None", cookie.getSameSite());
        assertTrue(cookie.isSecure());
    }

    @Test
    void keepsLocalHttpCookiesSameSiteLax() {
        var request = MockServerHttpRequest.get("http://localhost:8443/api/v1/auth/login").build();

        var cookie = AuthCookieSupport.accessCookie("token", 60, request);

        assertEquals("Lax", cookie.getSameSite());
        assertFalse(cookie.isSecure());
    }
}
