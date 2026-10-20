package com.x.bff.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/** Safe owner fields shown to a platform administrator during merchant review. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MerchantOwnerProfile(
        Long id,
        String username,
        String fullName,
        String phone,
        String email,
        String profileImage,
        Integer status,
        LocalDateTime lastLogin,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
