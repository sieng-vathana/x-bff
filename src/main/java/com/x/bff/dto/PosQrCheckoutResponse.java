package com.x.bff.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record PosQrCheckoutResponse(JsonNode order, JsonNode payment) {
}
