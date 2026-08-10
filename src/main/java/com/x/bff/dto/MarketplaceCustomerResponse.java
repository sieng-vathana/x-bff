package com.x.bff.dto;

public record MarketplaceCustomerResponse(
        Long id,
        Long userId,
        Long businessId,
        Long storeId,
        String fullName,
        String phone,
        String email) {
}
