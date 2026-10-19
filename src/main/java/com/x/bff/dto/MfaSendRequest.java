package com.x.bff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MfaSendRequest(@NotBlank String username, @NotBlank String selectionToken,
                             @NotBlank @Pattern(regexp = "TELEGRAM|EMAIL") String method) { }
