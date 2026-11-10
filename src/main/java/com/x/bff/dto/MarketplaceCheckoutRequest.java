package com.x.bff.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MarketplaceCheckoutRequest(
        @NotNull @Positive Long storeId,
        @NotBlank @Pattern(regexp = "CUSTOMER_PICKUP|OWN_RIDER|THIRD_PARTY") String fulfillmentType,
        @NotBlank @Pattern(regexp = "COD|QR") String paymentType,
        @Positive Long deliveryQuoteId,
        @Valid DeliveryAddress deliveryAddress,
        @Pattern(regexp = "[A-Za-z0-9_-]{2,64}") String discountCode,
        @Size(max = 500) String note,
        @NotBlank @Size(min = 8, max = 64) String idempotencyKey,
        /** Optional: who the store calls about this order (used for pickup orders). */
        @Size(max = 160) String contactName,
        @Size(max = 32) String contactPhone) {

    public record DeliveryAddress(
            @NotBlank @Size(max = 160) String receiverName,
            @NotBlank @Size(max = 32) String phone,
            @NotBlank @Size(max = 100) String provinceCity,
            @Size(max = 100) String district,
            @Size(max = 100) String commune,
            @Size(max = 100) String village,
            @NotBlank @Size(max = 255) String streetAddress,
            @Size(max = 255) String landmark,
            @Size(max = 500) String deliveryInstructions,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude) {
    }
}
