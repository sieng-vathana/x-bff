package com.x.bff.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sharedlib.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class BffExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BffExceptionHandler.class);
    private final ObjectMapper objectMapper;

    public BffExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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
                "Downstream request failed status={} uri={}",
                ex.getStatusCode().value(),
                path);

        HttpStatusCode downstreamStatus = ex.getStatusCode();
        if (downstreamStatus.is4xxClientError()) {
            return ResponseEntity.status(downstreamStatus)
                    .body(ApiResponse.error(downstreamStatus.value(), downstreamMessage(body)));
        }

        return new ResponseEntity<>(
                ApiResponse.error(
                        HttpStatus.BAD_GATEWAY.value(),
                        "A downstream service could not complete the request."),
                HttpStatus.BAD_GATEWAY);
    }

    private String downstreamMessage(String body) {
        if (body != null && !body.isBlank()) {
            try {
                JsonNode messageNode = objectMapper.readTree(body).path("message");
                if (messageNode.isTextual()) {
                    String message = messageNode.asText().trim();
                    if (!message.isEmpty() && message.length() <= 200) {
                        return message;
                    }
                }
            } catch (Exception ignored) {
                // Fall back to a safe message when the downstream response is not our API envelope.
            }
        }
        return "The request was rejected by a downstream service.";
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

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(NoResourceFoundException ex) {
        return new ResponseEntity<>(ApiResponse.error(404, "Resource not found."), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String message = ex.getReason() != null ? ex.getReason() : "Request could not be processed.";
        return new ResponseEntity<>(ApiResponse.error(status.value(), message), status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("Unhandled BFF exception", ex);
        return new ResponseEntity<>(
                ApiResponse.error(500, "BFF internal error."),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
