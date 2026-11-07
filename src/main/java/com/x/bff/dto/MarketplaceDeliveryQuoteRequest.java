package com.x.bff.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record MarketplaceDeliveryQuoteRequest(
        @NotNull @Positive Long storeId,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal destinationLatitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal destinationLongitude,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode) {
}
