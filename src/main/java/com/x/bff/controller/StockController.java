package com.x.bff.controller;

import com.x.bff.dto.StockBalanceResponse;
import com.x.bff.dto.StockChangeRequest;
import com.x.bff.dto.StockReservationRequest;
import com.x.bff.service.ServiceClientFactory;
import com.sharedlib.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.core.ParameterizedTypeReference;
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

@RestController
@RequestMapping("/api/v1/stock")
public class StockController {
    private final WebClient inventoryClient;

    private static final ParameterizedTypeReference<ApiResponse<StockBalanceResponse>> STOCK_RESPONSE =
            new ParameterizedTypeReference<>() {};

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
