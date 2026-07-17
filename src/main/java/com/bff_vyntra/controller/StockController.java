package com.bff_vyntra.controller;

import com.bff_vyntra.service.ServiceClientFactory;
import com.bff_vyntra.utils.VyntraUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/stock")
public class StockController {

    private final WebClient stockClient;

    public StockController(ServiceClientFactory clientFactory) {
        this.stockClient = clientFactory.forService("inventory", "/api/v1/stock");
    }

    @GetMapping("/{stockId}")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_STOCK')")
    public Mono<ResponseEntity<?>> getStock(
            @PathVariable String stockId,
            @RequestParam(required = false) String search,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        return stockClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/{stockId}")
                        .queryParamIfPresent("search", Optional.ofNullable(search))
                        .build(stockId))
                .header("X-User-Id", userId != null ? userId : "")
                .header("Device-Type", "mobile_app")
                .retrieve()
                .bodyToMono(String.class)
                .map(VyntraUtil::toJsonResponse);
    }

    @PostMapping("/in")
    @PreAuthorize("hasAnyAuthority('M004S02:1')")
    public Mono<ResponseEntity<?>> stockIn(@RequestBody Object request) {
        return stockClient.post()
                .uri("/in")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .map(VyntraUtil::toJsonResponse);
    }

    @PostMapping("/out")
    @PreAuthorize("hasAnyAuthority('M004S02:1')")
    public Mono<ResponseEntity<?>> stockOut(@RequestBody Object request) {
        return stockClient.post()
                .uri("/out")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .map(VyntraUtil::toJsonResponse);
    }
}
