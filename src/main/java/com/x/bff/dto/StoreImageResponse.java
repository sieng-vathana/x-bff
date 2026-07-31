package com.x.bff.dto;

public record StoreImageResponse(Long id, String imageUrl, Boolean isPrimary, Integer sortOrder) {
}
