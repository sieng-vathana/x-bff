package com.x.bff.dto;

import java.util.List;

/** Merchant account, its owner contact, and registered stores for backoffice review. */
public record MerchantProfileResponse(
        BusinessResponse merchant,
        MerchantOwnerProfile owner,
        List<StoreResponse> stores) {
}
