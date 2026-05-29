package com.bff_vyntra.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/stock")
@RequiredArgsConstructor
public class StockController {

    private final com.bff_vyntra.service.ServiceClientFactory clientFactory;
    private org.springframework.web.reactive.function.client.WebClient stockClient;

    @jakarta.annotation.PostConstruct
    void init() {
        this.stockClient = clientFactory.forService("/api/v1/stock");
    }

    @GetMapping("/{stockId}")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_STOCK')")
    public Mono<org.springframework.http.ResponseEntity<?>> getStock(
            @PathVariable String stockId,
            @RequestParam(required = false) String search,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        return stockClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/{stockId}")
                        .queryParamIfPresent("search", java.util.Optional.ofNullable(search))
                        .build(stockId))
                .header("X-User-Id", userId != null ? userId : "")
                .header("Device-Type", "mobile_app")
                .retrieve()
                .bodyToMono(String.class)
                .map(com.bff_vyntra.utils.VyntraUtil::toJsonResponse);
    }

    @org.springframework.web.bind.annotation.PostMapping("/in")
    @PreAuthorize("hasAnyAuthority('M004S02:1')")
    public Mono<org.springframework.http.ResponseEntity<?>> stockIn(@org.springframework.web.bind.annotation.RequestBody Object request) {
        return stockClient.post()
                .uri("/in")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .map(com.bff_vyntra.utils.VyntraUtil::toJsonResponse);
    }

    @org.springframework.web.bind.annotation.PostMapping("/out")
    @PreAuthorize("hasAnyAuthority('M004S02:1')")
    public Mono<org.springframework.http.ResponseEntity<?>> stockOut(@org.springframework.web.bind.annotation.RequestBody Object request) {
        return stockClient.post()
                .uri("/out")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .map(com.bff_vyntra.utils.VyntraUtil::toJsonResponse);
    }
}
