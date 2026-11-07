package com.x.bff.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;

public record MarketplaceCartItemRequest(
        @NotNull @Positive Long productId,
        @NotNull @Positive Long storeId,
        @NotNull @Positive Long variantId,
        @NotNull @Positive @Max(999) Integer quantity) {
}
