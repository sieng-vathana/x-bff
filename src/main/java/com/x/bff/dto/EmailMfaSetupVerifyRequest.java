package com.x.bff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailMfaSetupVerifyRequest(@NotBlank String challengeToken,
                                         @Pattern(regexp = "\\d{8}") String code) { }
