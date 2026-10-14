package com.x.bff.dto;

public record MarketplaceStoreResponse(
        Long id,
        String name,
        String code,
        String storeType,
        String city,
        String countryCode,
        String image,
        StoreOpeningHours openingHours) {

    public MarketplaceStoreResponse(Long id, String name, String code, String city, String countryCode, String image) {
        this(id, name, code, null, city, countryCode, image, null);
    }
}

