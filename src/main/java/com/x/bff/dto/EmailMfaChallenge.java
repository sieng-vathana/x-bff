package com.x.bff.dto;

import java.time.Instant;

public record EmailMfaChallenge(String challengeToken, Instant expiresAt, String maskedEmail) { }
