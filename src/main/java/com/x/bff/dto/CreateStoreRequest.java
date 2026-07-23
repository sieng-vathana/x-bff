package com.x.bff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record CreateStoreRequest(
        @NotNull(message = "Business ID is required")
        @Positive(message = "Business ID must be positive") Long businessId,
        @NotBlank(message = "Store name is required")
        @Size(max = 160, message = "Store name must not exceed 160 characters") String name,
        @NotBlank(message = "Store code is required")
        @Size(max = 64, message = "Store code must not exceed 64 characters") String code,
        @NotBlank(message = "Address line 1 is required")
        @Size(max = 255, message = "Address line 1 must not exceed 255 characters") String addressLine1,
        @Size(max = 255, message = "Address line 2 must not exceed 255 characters") String addressLine2,
        @Size(max = 255, message = "Landmark must not exceed 255 characters") String landmark,
        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must not exceed 100 characters") String city,
        @Size(max = 100, message = "State/province must not exceed 100 characters") String stateProvince,
        @NotBlank(message = "Country code is required")
        @Pattern(regexp = "[A-Za-z]{2}", message = "Country code must contain 2 letters") String countryCode,
        @Size(max = 20, message = "Postal code must not exceed 20 characters") String postalCode,
        @Size(max = 32, message = "Phone must not exceed 32 characters") String phone,
        @Size(max = 32, message = "Alternate phone must not exceed 32 characters") String alternatePhone,
        @Size(max = 254, message = "Email must not exceed 254 characters") String email,
        @Size(max = 255, message = "Website must not exceed 255 characters") String website,
        BigDecimal latitude,
        BigDecimal longitude) {
}
