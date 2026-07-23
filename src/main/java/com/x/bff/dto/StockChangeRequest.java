package com.x.bff.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StockChangeRequest(
        @NotNull @Positive Long storeId,
        @NotNull @Positive Long variantId,
        @Positive long quantity,
        String referenceType,
        String referenceId,
        Long performedBy,
        String note) {
}
