package com.x.bff.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class GuestOrderTokensTest {

    private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

    private final GuestOrderTokens tokens = new GuestOrderTokens("test-secret", Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void tokenOpensOnlyItsOwnOrder() {
        String token = tokens.issue(42L);

        assertThat(tokens.verify(42L, token)).isTrue();
        assertThat(tokens.verify(43L, token)).isFalse();
    }

    @Test
    void rejectsMissingMalformedAndTamperedTokens() {
        String token = tokens.issue(42L);

        assertThat(tokens.verify(42L, null)).isFalse();
        assertThat(tokens.verify(42L, "")).isFalse();
        assertThat(tokens.verify(42L, "not-a-token")).isFalse();
        assertThat(tokens.verify(42L, token + "x")).isFalse();
        String laterExpiry = (Long.parseLong(token.substring(0, token.indexOf('.'))) + 1) + token.substring(token.indexOf('.'));
        assertThat(tokens.verify(42L, laterExpiry)).isFalse();
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        GuestOrderTokens other = new GuestOrderTokens("other-secret", Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(tokens.verify(42L, other.issue(42L))).isFalse();
    }

    @Test
    void expiresAfterItsLifetime() {
        String token = tokens.issue(42L);
        GuestOrderTokens later = new GuestOrderTokens("test-secret",
                Clock.fixed(NOW.plus(GuestOrderTokens.LIFETIME).plusSeconds(1), ZoneOffset.UTC));

        assertThat(later.verify(42L, token)).isFalse();
    }
}
