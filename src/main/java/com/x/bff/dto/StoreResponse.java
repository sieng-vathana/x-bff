package com.x.bff.dto;

import java.time.LocalDateTime;
import java.util.List;

public record StoreResponse(
        Long id,
        Long businessId,
        String name,
        String code,
        String storeType,
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
        List<StoreImageResponse> images,
        Integer status,
        String marketplaceStatus,
        LocalDateTime marketplaceAppliedAt,
        LocalDateTime marketplaceApprovedAt,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public StoreResponse(Long id, Long businessId, String name, String code,
                         String addressLine1, String addressLine2, String landmark,
                         String city, String stateProvince, String countryCode,
                         String postalCode, String phone, String alternatePhone,
                         String email, String website, java.math.BigDecimal latitude,
                         java.math.BigDecimal longitude, List<StoreImageResponse> images,
                         Integer status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, businessId, name, code, "GENERAL_RETAIL", addressLine1, addressLine2,
             landmark, city, stateProvince, countryCode, postalCode, phone, alternatePhone,
             email, website, latitude, longitude, images, status, "NOT_LISTED",
             null, null, null, createdAt, updatedAt);
    }
}
