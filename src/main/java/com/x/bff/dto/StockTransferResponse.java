package com.x.bff.dto;

import java.time.LocalDateTime;
import java.util.List;

public record StockTransferResponse(
        Long id, Long sourceStoreId, Long destinationStoreId, String status, String note, String resolutionNote,
        Long requestedBy, Long approvedBy, Long dispatchedBy, Long receivedBy,
        LocalDateTime createdAt, LocalDateTime approvedAt, LocalDateTime dispatchedAt, LocalDateTime receivedAt,
        List<Line> lines) {
    public record Line(Long id, Long variantId, String productName, String variantName, String sku,
                       long quantityRequested, long quantityDispatched, long quantityReceived, long quantityMissing) {
    }
}
