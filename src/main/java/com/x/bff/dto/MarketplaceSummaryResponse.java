package com.x.bff.dto;

import java.math.BigDecimal;
import java.util.List;

public record MarketplaceSummaryResponse(
        MarketplaceCustomerResponse customer,
        List<Long> favoriteProductIds,
        int favoriteCount,
        int cartItemCount,
        BigDecimal cartSubtotal,
        String cartCurrency,
        List<MarketplaceCartItemResponse> cartItems) {
}
