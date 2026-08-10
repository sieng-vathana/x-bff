package com.x.bff.dto;

import java.math.BigDecimal;

public record MarketplaceProductView(
        Long productId,
        String productName,
        String shortName,
        String thumbnail,
        String description,
        Long categoryId,
        String categoryName,
        String currencyCode,
        BigDecimal onlinePrice,
        BigDecimal compareAtPrice,
        Integer quantity,
        Boolean featured,
        MarketplaceStoreResponse store) {
}
