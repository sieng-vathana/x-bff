package com.x.bff.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record StockReservationRequest(
        @NotNull @Positive Long storeId,
        @NotNull @Positive Long variantId,
        @NotNull @Positive Long orderId,
        @Positive long quantity,
        @NotNull LocalDateTime expiresAt) {
}
