package com.x.bff.controller;

import com.sharedlib.response.ApiResponse;
import com.sharedlib.response.PageResponse;
import com.x.bff.dto.StockBalanceResponse;
import com.x.bff.dto.StockChangeRequest;
import com.x.bff.dto.StockMovementResponse;
import com.x.bff.dto.StockReservationRequest;
import com.x.bff.service.ServiceClientFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/stock")
public class StockController {
    private final WebClient inventoryClient;

    private static final ParameterizedTypeReference<ApiResponse<StockBalanceResponse>> STOCK_RESPONSE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<PageResponse<StockMovementResponse>>>
            STOCK_MOVEMENTS_RESPONSE = new ParameterizedTypeReference<>() {};

    public StockController(ServiceClientFactory clientFactory) {
        this.inventoryClient = clientFactory.forService("inventory", "/api/v1/inventory");
    }

    @GetMapping("/balance")
    @PreAuthorize("hasAuthority('x-inventory:read')")
    public Mono<ResponseEntity<ApiResponse<StockBalanceResponse>>> getBalance(
            @RequestParam Long storeId, @RequestParam Long variantId) {
        return inventoryClient.get().uri(uri -> uri.path("/balance").queryParam("storeId", storeId)
                        .queryParam("variantId", variantId).build())
                .retrieve().bodyToMono(STOCK_RESPONSE)
                .map(ApiResponse::getData)
                .map(data -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), data)))
                .onErrorResume(org.springframework.web.reactive.function.client.WebClientResponseException.NotFound.class,
                        ignored -> Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.error(HttpStatus.NOT_FOUND.value(),
                                        "Stock balance not found for store " + storeId + " and variant " + variantId))));
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAuthority('x-inventory:read')")
    public Mono<ResponseEntity<ApiResponse<PageResponse<StockMovementResponse>>>> getMovements(
            @RequestParam @Positive Long storeId,
            @RequestParam(required = false) @Positive Long variantId,
            @RequestParam(required = false) String movementType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return inventoryClient.get().uri(uri -> {
                    var builder = uri.path("/movements").queryParam("storeId", storeId);
                    if (variantId != null) {
                        builder.queryParam("variantId", variantId);
                    }
                    if (movementType != null && !movementType.isBlank()) {
                        builder.queryParam("movementType", movementType);
                    }
                    if (from != null) {
                        builder.queryParam("from", from);
                    }
                    if (to != null) {
                        builder.queryParam("to", to);
                    }
                    return builder.queryParam("page", page).queryParam("size", size).build();
                })
                .retrieve().bodyToMono(STOCK_MOVEMENTS_RESPONSE)
                .map(ApiResponse::getData)
                .map(data -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), data)));
    }

    @PostMapping("/in")
    @PreAuthorize("hasAuthority('x-inventory:stock-in')")
    public Mono<ResponseEntity<ApiResponse<StockBalanceResponse>>> stockIn(@Valid @RequestBody StockChangeRequest request) {
        return post("/stock-in", request, "Stock received", HttpStatus.CREATED);
    }

    @PostMapping("/out")
    @PreAuthorize("hasAuthority('x-inventory:stock-out')")
    public Mono<ResponseEntity<ApiResponse<StockBalanceResponse>>> stockOut(@Valid @RequestBody StockChangeRequest request) {
        return post("/stock-out", request, "Stock removed", HttpStatus.OK);
    }

    @PostMapping("/reservations")
    @PreAuthorize("hasAuthority('x-inventory:stock-out')")
    public Mono<ResponseEntity<ApiResponse<StockBalanceResponse>>> reserve(@Valid @RequestBody StockReservationRequest request) {
        return post("/reservations", request, "Stock reserved", HttpStatus.CREATED);
    }

    private Mono<ResponseEntity<ApiResponse<StockBalanceResponse>>> post(
            String path, Object request, String message, HttpStatus status) {
        return inventoryClient.post().uri(path).bodyValue(request).retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<StockBalanceResponse>>() {})
                .map(ApiResponse::getData)
                .map(data -> ResponseEntity.status(status).body(ApiResponse.success(status.value(), message, data)));
    }
}
