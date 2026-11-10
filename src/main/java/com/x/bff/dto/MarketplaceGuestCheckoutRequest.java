package com.x.bff.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Checkout without an account. The cart lives in the browser, so the items come with the request;
 * the contact name and phone are how the store reaches the buyer for both delivery and pickup.
 */
public record MarketplaceGuestCheckoutRequest(
        @NotNull @Positive Long storeId,
        @NotEmpty @Size(max = 50) List<@Valid Item> items,
        @NotBlank @Size(max = 160) String contactName,
        @NotBlank @Pattern(regexp = "\\+?[0-9 ()-]{6,32}", message = "Enter a valid phone number") String contactPhone,
        @NotBlank @Pattern(regexp = "CUSTOMER_PICKUP|OWN_RIDER|THIRD_PARTY") String fulfillmentType,
        @NotBlank @Pattern(regexp = "COD|QR") String paymentType,
        @Positive Long deliveryQuoteId,
        @Valid MarketplaceCheckoutRequest.DeliveryAddress deliveryAddress,
        @Pattern(regexp = "[A-Za-z0-9_-]{2,64}") String discountCode,
        @Size(max = 500) String note,
        @NotBlank @Size(min = 8, max = 64) String idempotencyKey) {

    public record Item(
            @NotNull @Positive Long productId,
            @NotNull @Positive Long variantId,
            @NotNull @Positive @Max(999) Integer quantity) {
    }
}
