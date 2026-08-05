package com.x.bff.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.x.bff.service.ServiceClientFactory;
import com.x.bff.utils.XUtil;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final WebClient productClient;

    public ProductController(ServiceClientFactory clientFactory) {
        this.productClient = clientFactory.forService("product", "/api/v1/products");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('x-product:read')")
    public Mono<ResponseEntity<?>> getProducts() {
        return forward(productClient.get());
    }

    @GetMapping("/units")
    @PreAuthorize("hasAuthority('x-product:unit')")
    public Mono<ResponseEntity<?>> getUnits(
            @RequestParam Long businessId,
            @RequestParam(required = false) String storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long numericStoreId = parseLongQuietly(storeId);
        return forward(productClient.get().uri(uri -> {
            uri.path("/units")
                    .queryParam("businessId", businessId)
                    .queryParam("page", page)
                    .queryParam("size", size);
            if (numericStoreId != null) {
                uri.queryParam("storeId", numericStoreId);
            }
            return uri.build();
        }));
    }

    @GetMapping("/units/{id}")
    @PreAuthorize("hasAuthority('x-product:unit')")
    public Mono<ResponseEntity<?>> getUnit(
            @PathVariable Long id,
            @RequestParam Long businessId) {
        return forward(productClient.get().uri(uri -> uri.path("/units/{id}")
                .queryParam("businessId", businessId).build(id)));
    }

    @PostMapping("/units")
    @PreAuthorize("hasAuthority('x-product:unit')")
    public Mono<ResponseEntity<?>> createUnit(@RequestBody JsonNode request) {
        return forward(productClient.post().uri("/units")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request));
    }

    @PutMapping("/units/{id}")
    @PreAuthorize("hasAuthority('x-product:unit')")
    public Mono<ResponseEntity<?>> updateUnit(
            @PathVariable Long id,
            @RequestParam Long businessId,
            @RequestBody JsonNode request) {
        return forward(productClient.put().uri(uri -> uri.path("/units/{id}")
                        .queryParam("businessId", businessId).build(id))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request));
    }

    @DeleteMapping("/units/{id}")
    @PreAuthorize("hasAuthority('x-product:unit')")
    public Mono<ResponseEntity<?>> deleteUnit(
            @PathVariable Long id,
            @RequestParam Long businessId) {
        return forward(productClient.delete().uri(uri -> uri.path("/units/{id}")
                .queryParam("businessId", businessId).build(id)));
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('x-product:category')")
    public Mono<ResponseEntity<?>> getCategories(
            @RequestParam Long businessId,
            @RequestParam(required = false) String storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long numericStoreId = parseLongQuietly(storeId);
        return forward(productClient.get().uri(uri -> {
            uri.path("/categories")
                    .queryParam("businessId", businessId)
                    .queryParam("page", page)
                    .queryParam("size", size);
            if (numericStoreId != null) {
                uri.queryParam("storeId", numericStoreId);
            }
            return uri.build();
        }));
    }

    @GetMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('x-product:category')")
    public Mono<ResponseEntity<?>> getCategory(
            @PathVariable Long id,
            @RequestParam Long businessId) {
        return forward(productClient.get().uri(uri -> uri.path("/categories/{id}")
                .queryParam("businessId", businessId).build(id)));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('x-product:category')")
    public Mono<ResponseEntity<?>> createCategory(@RequestBody JsonNode request) {
        return forward(productClient.post().uri("/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('x-product:category')")
    public Mono<ResponseEntity<?>> updateCategory(
            @PathVariable Long id,
            @RequestParam Long businessId,
            @RequestBody JsonNode request) {
        return forward(productClient.put().uri(uri -> uri.path("/categories/{id}")
                        .queryParam("businessId", businessId).build(id))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('x-product:category')")
    public Mono<ResponseEntity<?>> deleteCategory(
            @PathVariable Long id,
            @RequestParam Long businessId) {
        return forward(productClient.delete().uri(uri -> uri.path("/categories/{id}")
                .queryParam("businessId", businessId).build(id)));
    }

    @GetMapping("/brands")
    @PreAuthorize("hasAuthority('x-product:brand') or hasAuthority('x-product:read')")
    public Mono<ResponseEntity<?>> getBrands(
            @RequestParam Long businessId,
            @RequestParam(required = false) String storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long numericStoreId = parseLongQuietly(storeId);
        return forward(productClient.get().uri(uri -> {
            uri.path("/brands")
                    .queryParam("businessId", businessId)
                    .queryParam("page", page)
                    .queryParam("size", size);
            if (numericStoreId != null) {
                uri.queryParam("storeId", numericStoreId);
            }
            return uri.build();
        }));
    }

    @GetMapping("/brands/{id}")
    @PreAuthorize("hasAuthority('x-product:brand') or hasAuthority('x-product:read')")
    public Mono<ResponseEntity<?>> getBrand(
            @PathVariable Long id,
            @RequestParam Long businessId) {
        return forward(productClient.get().uri(uri -> uri.path("/brands/{id}")
                .queryParam("businessId", businessId).build(id)));
    }

    @PostMapping("/brands")
    @PreAuthorize("hasAuthority('x-product:brand')")
    public Mono<ResponseEntity<?>> createBrand(@RequestBody JsonNode request) {
        return forward(productClient.post().uri("/brands")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request));
    }

    @PutMapping("/brands/{id}")
    @PreAuthorize("hasAuthority('x-product:brand')")
    public Mono<ResponseEntity<?>> updateBrand(
            @PathVariable Long id,
            @RequestParam Long businessId,
            @RequestBody JsonNode request) {
        return forward(productClient.put().uri(uri -> uri.path("/brands/{id}")
                        .queryParam("businessId", businessId).build(id))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request));
    }

    @DeleteMapping("/brands/{id}")
    @PreAuthorize("hasAuthority('x-product:brand')")
    public Mono<ResponseEntity<?>> deleteBrand(
            @PathVariable Long id,
            @RequestParam Long businessId) {
        return forward(productClient.delete().uri(uri -> uri.path("/brands/{id}")
                .queryParam("businessId", businessId).build(id)));
    }

    private Mono<ResponseEntity<?>> forward(WebClient.RequestHeadersSpec<?> request) {
        return request.exchangeToMono(response -> response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> XUtil.toJsonResponse(body, response.statusCode())));
    }

    private Long parseLongQuietly(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
