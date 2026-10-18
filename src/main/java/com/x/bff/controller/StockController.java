package com.x.bff.controller;

import com.sharedlib.response.ApiResponse;
import com.sharedlib.response.PageResponse;
import com.x.bff.dto.StockBalanceResponse;
import com.x.bff.dto.StockChangeRequest;
import com.x.bff.dto.ExpiryBatchResponse;
import com.x.bff.dto.ExpiryBatchUpdateRequest;
import com.x.bff.dto.ExpirySummaryResponse;
import com.x.bff.dto.ExpiryWriteOffRequest;
import com.x.bff.dto.StockMovementResponse;
import com.x.bff.dto.StockReservationRequest;
import com.x.bff.dto.CreateStockTransferRequest;
import com.x.bff.dto.ReceiveStockTransferRequest;
import com.x.bff.dto.StockTransferCommand;
import com.x.bff.dto.StockTransferResponse;
import com.x.bff.service.ServiceClientFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private static final ParameterizedTypeReference<ApiResponse<PageResponse<StockBalanceResponse>>>
            LOW_STOCK_RESPONSE = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<PageResponse<ExpiryBatchResponse>>>
            EXPIRY_BATCHES_RESPONSE = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<ExpirySummaryResponse>>
            EXPIRY_SUMMARY_RESPONSE = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<PageResponse<StockTransferResponse>>>
            STOCK_TRANSFERS_RESPONSE = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<StockTransferResponse>>
            STOCK_TRANSFER_RESPONSE = new ParameterizedTypeReference<>() {};

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

    @GetMapping("/reports/low-stock")
    @PreAuthorize("hasAuthority('x-inventory:read')")
    public Mono<ResponseEntity<ApiResponse<PageResponse<StockBalanceResponse>>>> lowStock(
            @RequestParam @Positive Long storeId,
            @RequestParam(defaultValue = "5") @PositiveOrZero long threshold,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return inventoryClient.get().uri(uri -> uri.path("/reports/low-stock")
                        .queryParam("storeId", storeId)
                        .queryParam("threshold", threshold)
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())
                .retrieve().bodyToMono(LOW_STOCK_RESPONSE)
                .map(ApiResponse::getData)
                .map(data -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), data)));
    }

    @GetMapping("/expiry/batches")
    @PreAuthorize("hasAuthority('x-inventory:read')")
    public Mono<ResponseEntity<ApiResponse<PageResponse<ExpiryBatchResponse>>>> expiryBatches(
            @RequestParam @Positive Long storeId,
            @RequestParam(defaultValue = "ALL") String filter,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return inventoryClient.get().uri(uri -> uri.path("/expiry/batches")
                        .queryParam("storeId", storeId).queryParam("filter", filter)
                        .queryParam("page", page).queryParam("size", size).build())
                .retrieve().bodyToMono(EXPIRY_BATCHES_RESPONSE)
                .map(ApiResponse::getData)
                .map(data -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), data)));
    }

    @GetMapping("/expiry/summary")
    @PreAuthorize("hasAuthority('x-inventory:read')")
    public Mono<ResponseEntity<ApiResponse<ExpirySummaryResponse>>> expirySummary(@RequestParam @Positive Long storeId) {
        return inventoryClient.get().uri(uri -> uri.path("/expiry/summary").queryParam("storeId", storeId).build())
                .retrieve().bodyToMono(EXPIRY_SUMMARY_RESPONSE)
                .map(ApiResponse::getData)
                .map(data -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), data)));
    }

    @GetMapping("/transfers")
    @PreAuthorize("hasAuthority('x-inventory:read')")
    public Mono<ResponseEntity<ApiResponse<PageResponse<StockTransferResponse>>>> transfers(
            @RequestParam @Positive Long storeId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return inventoryClient.get().uri(uri -> {
                    var builder = uri.path("/transfers").queryParam("storeId", storeId)
                            .queryParam("page", page).queryParam("size", size);
                    if (status != null && !status.isBlank()) builder.queryParam("status", status);
                    return builder.build();
                }).retrieve().bodyToMono(STOCK_TRANSFERS_RESPONSE)
                .map(ApiResponse::getData)
                .map(data -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), data)));
    }

    @GetMapping("/transfers/{id}")
    @PreAuthorize("hasAuthority('x-inventory:read')")
    public Mono<ResponseEntity<ApiResponse<StockTransferResponse>>> transfer(@PathVariable @Positive Long id) {
        return transferResponse(inventoryClient.get().uri("/transfers/{id}", id));
    }

    @PostMapping("/transfers")
    @PreAuthorize("hasAuthority('x-inventory:create')")
    public Mono<ResponseEntity<ApiResponse<StockTransferResponse>>> createTransfer(
            @Valid @RequestBody CreateStockTransferRequest request) {
        return transferResponse(inventoryClient.post().uri("/transfers").bodyValue(request),
                HttpStatus.CREATED, "Stock transfer requested");
    }

    @PostMapping("/transfers/{id}/approve")
    @PreAuthorize("hasAuthority('x-inventory:update')")
    public Mono<ResponseEntity<ApiResponse<StockTransferResponse>>> approveTransfer(
            @PathVariable @Positive Long id, @Valid @RequestBody StockTransferCommand request) {
        return transferResponse(inventoryClient.post().uri("/transfers/{id}/approve", id).bodyValue(request),
                HttpStatus.OK, "Stock transfer approved");
    }

    @PostMapping("/transfers/{id}/reject")
    @PreAuthorize("hasAuthority('x-inventory:update')")
    public Mono<ResponseEntity<ApiResponse<StockTransferResponse>>> rejectTransfer(
            @PathVariable @Positive Long id, @Valid @RequestBody StockTransferCommand request) {
        return transferResponse(inventoryClient.post().uri("/transfers/{id}/reject", id).bodyValue(request),
                HttpStatus.OK, "Stock transfer rejected");
    }

    @PostMapping("/transfers/{id}/cancel")
    @PreAuthorize("hasAuthority('x-inventory:update')")
    public Mono<ResponseEntity<ApiResponse<StockTransferResponse>>> cancelTransfer(
            @PathVariable @Positive Long id, @Valid @RequestBody StockTransferCommand request) {
        return transferResponse(inventoryClient.post().uri("/transfers/{id}/cancel", id).bodyValue(request),
                HttpStatus.OK, "Stock transfer cancelled");
    }

    @PostMapping("/transfers/{id}/dispatch")
    @PreAuthorize("hasAuthority('x-inventory:stock-out')")
    public Mono<ResponseEntity<ApiResponse<StockTransferResponse>>> dispatchTransfer(
            @PathVariable @Positive Long id, @Valid @RequestBody StockTransferCommand request) {
        return transferResponse(inventoryClient.post().uri("/transfers/{id}/dispatch", id).bodyValue(request),
                HttpStatus.OK, "Stock transfer dispatched");
    }

    @PostMapping("/transfers/{id}/receive")
    @PreAuthorize("hasAuthority('x-inventory:stock-in')")
    public Mono<ResponseEntity<ApiResponse<StockTransferResponse>>> receiveTransfer(
            @PathVariable @Positive Long id, @Valid @RequestBody ReceiveStockTransferRequest request) {
        return transferResponse(inventoryClient.post().uri("/transfers/{id}/receive", id).bodyValue(request),
                HttpStatus.OK, "Stock transfer receipt recorded");
    }

    @PostMapping("/expiry/batches/{batchId}/write-off")
    @PreAuthorize("hasAuthority('x-inventory:stock-out')")
    public Mono<ResponseEntity<ApiResponse<StockBalanceResponse>>> writeOffExpiredBatch(
            @PathVariable @Positive Long batchId,
            @RequestParam @Positive Long storeId,
            @Valid @RequestBody ExpiryWriteOffRequest request) {
        return inventoryClient.post().uri(uri -> uri.path("/expiry/batches/{batchId}/write-off")
                        .queryParam("storeId", storeId).build(batchId))
                .bodyValue(request).retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<StockBalanceResponse>>() {})
                .map(ApiResponse::getData)
                .map(data -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Expired stock written off", data)));
    }

    @PutMapping("/expiry/batches/{batchId}")
    @PreAuthorize("hasAuthority('x-inventory:update')")
    public Mono<ResponseEntity<ApiResponse<ExpiryBatchResponse>>> updateExpiryBatch(
            @PathVariable @Positive Long batchId,
            @RequestParam @Positive Long storeId,
            @Valid @RequestBody ExpiryBatchUpdateRequest request) {
        return inventoryClient.put().uri(uri -> uri.path("/expiry/batches/{batchId}")
                        .queryParam("storeId", storeId).build(batchId))
                .bodyValue(request).retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<ExpiryBatchResponse>>() {})
                .map(ApiResponse::getData)
                .map(data -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Stock batch updated", data)));
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

    private Mono<ResponseEntity<ApiResponse<StockTransferResponse>>> transferResponse(
            WebClient.RequestHeadersSpec<?> request) {
        return transferResponse(request, HttpStatus.OK, null);
    }

    private Mono<ResponseEntity<ApiResponse<StockTransferResponse>>> transferResponse(
            WebClient.RequestHeadersSpec<?> request, HttpStatus status, String message) {
        return request.retrieve().bodyToMono(STOCK_TRANSFER_RESPONSE)
                .map(ApiResponse::getData)
                .map(data -> ResponseEntity.status(status).body(message == null
                        ? ApiResponse.success(status.value(), data)
                        : ApiResponse.success(status.value(), message, data)));
    }
}
