package com.x.bff.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateStockTransferRequest(
        @NotNull @Positive Long sourceStoreId,
        @NotNull @Positive Long destinationStoreId,
        @Size(max = 500) String note,
        Long performedBy,
        @NotEmpty @Size(max = 100) List<@Valid Line> lines) {
    public record Line(@NotNull @Positive Long variantId, @Positive long quantity) {
    }
}
