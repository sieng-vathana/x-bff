package com.x.bff.dto;

public record MarketplaceCategoryResponse(
        Long id,
        String name,
        String image,
        Boolean featured) {
}
