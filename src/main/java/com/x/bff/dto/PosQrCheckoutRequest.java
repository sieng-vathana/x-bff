package com.x.bff.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record PosQrCheckoutRequest(
        @NotNull @Positive Long businessId,
        @NotNull @Positive Long storeId,
        @NotNull @PositiveOrZero Long customerId,
        @NotNull @Positive Long cashierId,
        @NotBlank @Size(max = 3) String currencyCode,
        @DecimalMin("0.0") BigDecimal taxRate,
        @NotBlank @Size(max = 160) String idempotencyKey,
        @NotBlank @Size(max = 160) String paymentIdempotencyKey,
        @Size(max = 500) String paymentNote,
        @NotEmpty List<@Valid PosOrderItemRequest> items) {
}
