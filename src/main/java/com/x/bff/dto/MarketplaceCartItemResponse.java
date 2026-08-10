package com.x.bff.dto;

import java.math.BigDecimal;

public record MarketplaceCartItemResponse(
        Long productId,
        Long storeId,
        Long variantId,
        String productName,
        String thumbnail,
        BigDecimal unitPrice,
        String currencyCode,
        Integer quantity,
        BigDecimal lineTotal) {
}
