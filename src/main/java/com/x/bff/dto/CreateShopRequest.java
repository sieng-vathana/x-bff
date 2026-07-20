package com.x.bff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateShopRequest(
        @NotNull(message = "Business ID is required")
        @Positive(message = "Business ID must be positive") Long businessId,
        @NotBlank(message = "Shop name is required")
        @Size(max = 160, message = "Shop name must not exceed 160 characters") String name,
        @NotBlank(message = "Shop code is required")
        @Size(max = 64, message = "Shop code must not exceed 64 characters") String code) {
}
