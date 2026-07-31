package com.x.bff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record StoreImageRequest(
        @NotBlank(message = "Image URL is required")
        @Size(max = 2_000, message = "Image URL must not exceed 2000 characters") String imageUrl,
        Boolean isPrimary,
        @PositiveOrZero(message = "Sort order must be zero or greater") Integer sortOrder) {
}
