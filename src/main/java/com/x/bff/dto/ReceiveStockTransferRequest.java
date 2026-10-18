package com.x.bff.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReceiveStockTransferRequest(
        Long performedBy,
        @Size(max = 500) String note,
        @NotEmpty @Size(max = 100) List<@Valid Line> lines) {
    public record Line(@NotNull @Positive Long lineId, @PositiveOrZero long receivedQuantity, @PositiveOrZero long missingQuantity) {
    }
}
