package com.x.bff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** A new KHQR for a POS order that was created earlier but not paid yet. */
public record PosQrResumeRequest(
        @NotNull @Positive Long businessId,
        @NotNull @Positive Long storeId,
        @NotNull @Positive Long cashierId,
        @NotBlank @Size(max = 160) String paymentIdempotencyKey,
        @Size(max = 500) String paymentNote) {
}
