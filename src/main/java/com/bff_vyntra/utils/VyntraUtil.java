package com.bff_vyntra.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sharedlib.response.ApiResponse;
import org.springframework.http.ResponseEntity;

public class VyntraUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parses a raw JSON string from a downstream service and wraps it in a standard ResponseEntity.
     */
    public static ResponseEntity<?> toJsonResponse(String rawResponse) {
        try {
            // Attempt to parse the raw string as JSON
            JsonNode jsonNode = objectMapper.readTree(rawResponse);
            return ResponseEntity.ok(jsonNode);
        } catch (JsonProcessingException e) {
            // If it's not valid JSON, wrap it in our standard ApiResponse format
            ApiResponse<String> fallbackResponse = ApiResponse.success(200, "Success", rawResponse);
            return ResponseEntity.ok(fallbackResponse);
        }
    }
}
