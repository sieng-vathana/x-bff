package com.x.bff.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sharedlib.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

public class XUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parses a raw JSON string from a downstream service and wraps it in a standard ResponseEntity.
     */
    public static ResponseEntity<?> toJsonResponse(String rawResponse) {
        return toJsonResponse(rawResponse, HttpStatus.OK);
    }

    public static ResponseEntity<?> toJsonResponse(String rawResponse, HttpStatusCode statusCode) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return ResponseEntity.status(statusCode).build();
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(rawResponse);
            return ResponseEntity.status(statusCode).body(jsonNode);
        } catch (JsonProcessingException e) {
            ApiResponse<String> fallbackResponse = ApiResponse.success(
                    statusCode.value(),
                    "Success",
                    rawResponse);
            return ResponseEntity.status(statusCode).body(fallbackResponse);
        }
    }
}
