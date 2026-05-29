package com.bff_vyntra.controller;

import com.bff_vyntra.service.ServiceClientFactory;
import com.bff_vyntra.utils.VyntraUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping({"/api/v1/products"})
@RequiredArgsConstructor
public class ProductController {

    private final ServiceClientFactory clientFactory;
    private WebClient productClient;

    @PostConstruct
    void init() {
        this.productClient = clientFactory.forService("/api/v1/products");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_VIEW_PRODUCTS')")
    public Mono<ResponseEntity<?>> getProducts() {
        return productClient.get()
                .retrieve()
                .bodyToMono(String.class)
                .map(VyntraUtil::toJsonResponse);
    }
}
