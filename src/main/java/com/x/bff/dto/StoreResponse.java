package com.x.bff.dto;

import java.time.LocalDateTime;

public record StoreResponse(
        Long id,
        Long businessId,
        String name,
        String code,
        String addressLine1,
        String addressLine2,
        String landmark,
        String city,
        String stateProvince,
        String countryCode,
        String postalCode,
        String phone,
        String alternatePhone,
        String email,
        String website,
        java.math.BigDecimal latitude,
        java.math.BigDecimal longitude,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
