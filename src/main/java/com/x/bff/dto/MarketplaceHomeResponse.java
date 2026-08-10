package com.x.bff.dto;

import com.sharedlib.response.PageResponse;

import java.util.List;

public record MarketplaceHomeResponse(
        PageResponse<MarketplaceProductView> products,
        List<MarketplaceCategoryResponse> categories,
        AccountSummary account,
        FavoriteSummary favorites,
        CartSummary cart) {

    public record AccountSummary(boolean authenticated, Long id, String username, String fullName, String email) {
    }

    public record FavoriteSummary(int count, List<Long> productIds) {
    }

    public record CartSummary(int itemCount, java.math.BigDecimal subtotal, String currency,
                              List<MarketplaceCartItemResponse> items) {
    }
}
