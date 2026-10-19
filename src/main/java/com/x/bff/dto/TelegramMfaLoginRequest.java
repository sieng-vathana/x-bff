package com.x.bff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TelegramMfaLoginRequest(
        @NotBlank String username,
        @NotBlank String challengeToken,
        @NotBlank @Size(min = 8, max = 8) String code) { }
