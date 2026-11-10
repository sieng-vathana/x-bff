package com.x.bff.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

/**
 * Signed, expiring access tokens for guest orders. A guest has no account, so whoever holds the
 * token returned at checkout may read that one order and pay for it. Format: {@code expiry.signature}
 * where the signature covers the order id and the expiry.
 */
@Service
public class GuestOrderTokens {

    static final Duration LIFETIME = Duration.ofDays(30);

    private final byte[] key;
    private final Clock clock;

    @Autowired
    public GuestOrderTokens(@Value("${marketplace.guest-order-secret:${jwt.secret}}") String secret) {
        this(secret, Clock.systemUTC());
    }

    GuestOrderTokens(String secret, Clock clock) {
        // Domain-separated so a guest token can never be confused with anything else signed by the same secret.
        this.key = ("x-bff:guest-order:" + secret).getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public String issue(long orderId) {
        long expiresAt = clock.instant().plus(LIFETIME).getEpochSecond();
        return expiresAt + "." + sign(orderId, expiresAt);
    }

    public boolean verify(long orderId, String token) {
        if (token == null) return false;
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) return false;
        long expiresAt;
        try {
            expiresAt = Long.parseLong(token.substring(0, dot));
        } catch (NumberFormatException exception) {
            return false;
        }
        if (expiresAt < clock.instant().getEpochSecond()) return false;
        byte[] expected = sign(orderId, expiresAt).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = token.substring(dot + 1).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    private String sign(long orderId, long expiresAt) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal((orderId + ":" + expiresAt).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }
}
