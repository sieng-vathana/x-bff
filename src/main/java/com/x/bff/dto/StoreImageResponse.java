package com.x.bff.dto;

public record StoreImageResponse(Long id, String imageUrl, Long fileId, Boolean isPrimary, Integer sortOrder) {
}
