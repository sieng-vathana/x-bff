package com.x.bff.dto;

import java.time.Instant;

public record TelegramMfaLink(String url, Instant expiresAt) { }
