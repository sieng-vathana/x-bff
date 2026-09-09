package com.x.bff.dto;

public record MarketplaceCategoryResponse(
        Long id,
        String name,
        String code,
        String image,
        Boolean featured) {

    public MarketplaceCategoryResponse(Long id, String name, String image, Boolean featured) {
        this(id, name, null, image, featured);
    }
}

