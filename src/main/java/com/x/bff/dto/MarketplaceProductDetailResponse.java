package com.x.bff.dto;

import java.math.BigDecimal;
import java.util.List;

public record MarketplaceProductDetailResponse(
        Long productId,
        Long storeId,
        String productName,
        String shortName,
        String description,
        String thumbnail,
        Long categoryId,
        String categoryName,
        String currencyCode,
        Boolean featured,
        List<String> images,
        List<Variant> variants,
        MarketplaceStoreResponse store) {

    public record Variant(
            Long variantId,
            String name,
            String image,
            BigDecimal onlinePrice,
            BigDecimal compareAtPrice,
            Integer quantity,
            boolean inStock,
            List<Attribute> attributes) {
    }

    public record Attribute(String name, String value) {
    }
}
