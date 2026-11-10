package com.x.bff.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** A guest's new order plus the token that lets the same buyer view and pay for it later. */
public record MarketplaceGuestOrderResponse(JsonNode order, String guestToken) {
}
