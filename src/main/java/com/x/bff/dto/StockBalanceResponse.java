package com.x.bff.dto;

public record StockBalanceResponse(Long id, Long storeId, Long variantId,
                                   long quantityOnHand, long quantityReserved, long availableQuantity) {
}
