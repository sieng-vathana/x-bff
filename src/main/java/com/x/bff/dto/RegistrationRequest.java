package com.x.bff.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RegistrationRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Size(min = 8, max = 100) String password,
        @Email @Size(max = 160) String email,
        @Size(max = 40) String phone,
        @NotBlank @Size(max = 160) String businessName,
        @NotBlank @Size(max = 64) String businessCode,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String defaultCurrencyCode,
        @Size(max = 100) String taxRegistrationNumber,
        @Size(max = 32) String taxRegistrationLabel,
        Boolean pricesIncludeTax,
        @NotBlank @Size(max = 64) String timeZone,
        @NotNull @Min(1) @Max(12) Integer fiscalYearStartMonth,
        @NotBlank @Size(max = 160) String storeName,
        @NotBlank @Size(max = 64) String storeCode,
        @NotBlank @Size(max = 255) String storeAddressLine1,
        @NotBlank @Size(max = 100) String storeCity,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String storeCountryCode,
        @Pattern(regexp = "(?i)POS|ONLINE|BOTH") String storeSalesChannel,
        BigDecimal storeLatitude,
        BigDecimal storeLongitude,
        @jakarta.validation.constraints.Positive(message = "USD to KHR exchange rate must be positive")
        BigDecimal usdToKhrExchangeRate,
        /** STARTER, BUSINESS or PRO; the merchant chooses it on the sign-up form. */
        @Pattern(regexp = "(?i)STARTER|BUSINESS|PRO") String plan,
        @Pattern(regexp = "(?i)MONTHLY|YEARLY") String billingCycle) {

    /** Keeps callers using the pre-channel registration payload compatible. */
    public RegistrationRequest(String fullName, String username, String password, String email, String phone,
                               String businessName, String businessCode, String defaultCurrencyCode,
                               String taxRegistrationNumber, String taxRegistrationLabel, Boolean pricesIncludeTax,
                               String timeZone, Integer fiscalYearStartMonth, String storeName, String storeCode,
                               String storeAddressLine1, String storeCity, String storeCountryCode,
                               BigDecimal storeLatitude, BigDecimal storeLongitude, BigDecimal usdToKhrExchangeRate,
                               String plan, String billingCycle) {
        this(fullName, username, password, email, phone, businessName, businessCode, defaultCurrencyCode,
                taxRegistrationNumber, taxRegistrationLabel, pricesIncludeTax, timeZone, fiscalYearStartMonth,
                storeName, storeCode, storeAddressLine1, storeCity, storeCountryCode, null, storeLatitude,
                storeLongitude, usdToKhrExchangeRate, plan, billingCycle);
    }
}
