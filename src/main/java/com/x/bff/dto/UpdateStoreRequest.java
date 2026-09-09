package com.x.bff.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record UpdateStoreRequest(
        @Size(max = 160, message = "Store name must not exceed 160 characters") String name,
        @Size(max = 64, message = "Store code must not exceed 64 characters") String code,
        String storeType,
        @Size(max = 255, message = "Address line 1 must not exceed 255 characters") String addressLine1,
        @Size(max = 255, message = "Address line 2 must not exceed 255 characters") String addressLine2,
        @Size(max = 255, message = "Landmark must not exceed 255 characters") String landmark,
        @Size(max = 100, message = "City must not exceed 100 characters") String city,
        @Size(max = 100, message = "State/province must not exceed 100 characters") String stateProvince,
        @Pattern(regexp = "[A-Za-z]{2}", message = "Country code must contain 2 letters") String countryCode,
        @Size(max = 20, message = "Postal code must not exceed 20 characters") String postalCode,
        @Size(max = 32, message = "Phone must not exceed 32 characters") String phone,
        @Size(max = 32, message = "Alternate phone must not exceed 32 characters") String alternatePhone,
        @Size(max = 254, message = "Email must not exceed 254 characters") String email,
        @Size(max = 255, message = "Website must not exceed 255 characters") String website,
        BigDecimal latitude,
        BigDecimal longitude,
        List<@Valid StoreImageRequest> images,
        Integer status) {

    public UpdateStoreRequest(String name, String code,
                              String addressLine1, String addressLine2, String landmark,
                              String city, String stateProvince, String countryCode,
                              String postalCode, String phone, String alternatePhone,
                              String email, String website, BigDecimal latitude,
                              BigDecimal longitude, List<StoreImageRequest> images,
                              Integer status) {
        this(name, code, "GENERAL_RETAIL", addressLine1, addressLine2, landmark,
             city, stateProvince, countryCode, postalCode, phone, alternatePhone,
             email, website, latitude, longitude, images, status);
    }
}
