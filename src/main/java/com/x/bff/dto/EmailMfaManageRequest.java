package com.x.bff.dto;

import jakarta.validation.constraints.NotBlank;

public record EmailMfaManageRequest(@NotBlank String currentPassword, Boolean enabled) { }
