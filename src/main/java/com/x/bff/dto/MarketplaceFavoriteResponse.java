package com.x.bff.dto;

import java.time.LocalDateTime;

public record MarketplaceFavoriteResponse(Long productId, Long storeId, LocalDateTime createdAt) {
}
