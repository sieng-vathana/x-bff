package com.x.bff.dto;

import java.time.LocalDateTime;

public record StockMovementResponse(
        Long id,
        Long storeId,
        Long variantId,
        String movementType,
        long quantityDelta,
        String referenceType,
        String referenceId,
        Long performedBy,
        String note,
        LocalDateTime createdAt) {
}
