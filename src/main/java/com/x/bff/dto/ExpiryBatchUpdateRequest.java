package com.x.bff.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ExpiryBatchUpdateRequest(
        @Size(max = 100) String batchCode,
        @NotNull LocalDate expiryDate,
        @Positive Long performedBy) {
}
