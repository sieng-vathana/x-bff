package com.bff_vyntra.exception;

import com.sharedlib.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@RestControllerAdvice
public class BffExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BffExceptionHandler.class);

    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleServiceUnavailable(WebClientRequestException ex) {
        log.warn("Downstream service is unavailable: {}", ex.getUri());
        return new ResponseEntity<>(
                ApiResponse.error(503, "A downstream service is currently unavailable."),
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiResponse<Void>> handleWebClientResponseException(WebClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        String path = ex.getRequest() != null ? String.valueOf(ex.getRequest().getURI()) : "unknown";
        log.warn(
                "Downstream request failed status={} uri={} body={}",
                ex.getStatusCode().value(),
                path,
                body == null || body.isBlank() ? "<empty>" : body);

        // Surface a short hint for local debugging (status -1 means business error wrapper)
        String hint = "Downstream request failed (" + ex.getStatusCode().value() + ") calling " + path;
        if (body != null && !body.isBlank() && body.length() < 500) {
            hint = hint + ": " + body;
        }
        return new ResponseEntity<>(
                ApiResponse.error(ex.getStatusCode().value(), hint),
                HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return new ResponseEntity<>(
                ApiResponse.error(401, "Invalid username, password, or refresh token."),
                HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return new ResponseEntity<>(ApiResponse.error(403, "Access denied."), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(WebExchangeBindException ex) {
        String message = ex.getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Validation failed");
        return new ResponseEntity<>(ApiResponse.error(400, message), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("Unhandled BFF exception", ex);
        return new ResponseEntity<>(
                ApiResponse.error(500, "BFF internal error."),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
