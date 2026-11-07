package com.x.bff.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;

public record MarketplaceQuantityRequest(@NotNull @Positive @Max(999) Integer quantity) {
}
