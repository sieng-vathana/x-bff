package com.bff_vyntra.exception;

import com.sharedlib.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;

@RestControllerAdvice
public class BffExceptionHandler {

    /**
     * Fallback mechanism when a downstream microservice is completely unavailable (Connection Refused).
     * This intercepts the WebClientRequestException thrown by the declarative HTTP interfaces.
     */
    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleServiceUnavailable(WebClientRequestException ex) {
        String uri = ex.getUri() != null ? ex.getUri().toString() : "";
        String serviceName = "Downstream";
        
        if (uri.contains("product-service")) serviceName = "Product";
        else if (uri.contains("order-service")) serviceName = "Order";
        else if (uri.contains("stock-service")) serviceName = "Stock";
        else if (uri.contains("user-service") || uri.contains("auth-service")) serviceName = "User";

        return new ResponseEntity<>(ApiResponse.error(503, serviceName + " Service is currently unavailable."), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(org.springframework.web.reactive.function.client.WebClientResponseException.class)
    public ResponseEntity<ApiResponse<Void>> handleWebClientResponseException(org.springframework.web.reactive.function.client.WebClientResponseException ex) {
        return new ResponseEntity<>(ApiResponse.error(ex.getStatusCode().value(), "Downstream Error: " + ex.getResponseBodyAsString()), ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        ex.printStackTrace(); // print to your IDE console for debugging
        return new ResponseEntity<>(ApiResponse.error(500, "BFF Internal Error: " + ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
