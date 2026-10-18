package com.x.bff.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExpiryBatchResponse(
        Long id,
        Long storeId,
        Long variantId,
        String batchCode,
        LocalDate expiryDate,
        long quantityReceived,
        long quantityOnHand,
        long availableQuantity,
        String sourceType,
        String sourceReference,
        LocalDateTime receivedAt) {
}
