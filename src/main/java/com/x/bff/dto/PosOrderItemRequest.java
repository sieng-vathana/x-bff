package com.x.bff.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PosOrderItemRequest(
        @NotNull @Positive Long variantId,
        @Positive int quantity,
        String discountType,
        BigDecimal discountValue,
        String discountReason) {
}
