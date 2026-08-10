package com.x.bff.dto;

public record MarketplaceStoreResponse(
        Long id,
        String name,
        String code,
        String city,
        String countryCode,
        String image) {
}
