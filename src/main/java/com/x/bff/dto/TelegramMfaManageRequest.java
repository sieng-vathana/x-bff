package com.x.bff.dto;

import jakarta.validation.constraints.NotBlank;

public record TelegramMfaManageRequest(@NotBlank String currentPassword, Boolean enabled) { }
