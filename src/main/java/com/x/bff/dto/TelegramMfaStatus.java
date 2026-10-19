package com.x.bff.dto;

public record TelegramMfaStatus(boolean botConfigured, String botUsername, boolean connected, boolean enabled, boolean linkPending) { }
