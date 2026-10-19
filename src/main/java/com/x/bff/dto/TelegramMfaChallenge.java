package com.x.bff.dto;

import java.time.Instant;

public record TelegramMfaChallenge(String challengeToken, Instant expiresAt) { }
