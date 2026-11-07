package com.x.bff.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MarketplaceDeliveryQuoteResponse(
        Long id,
        Long storeId,
        String providerType,
        BigDecimal distanceKm,
        BigDecimal quotedFee,
        String currencyCode,
        LocalDateTime expiresAt,
        BigDecimal destinationLatitude,
        BigDecimal destinationLongitude) {
}
