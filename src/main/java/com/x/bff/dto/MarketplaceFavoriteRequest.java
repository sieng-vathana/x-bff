package com.x.bff.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MarketplaceFavoriteRequest(
        @NotNull @Positive Long productId,
        @Positive Long storeId) {
}
