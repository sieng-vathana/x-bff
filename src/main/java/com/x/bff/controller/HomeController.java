package com.x.bff.controller;

import com.x.bff.service.ServiceClientFactory;
import com.x.bff.utils.XUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/home")
public class HomeController {

    private final WebClient homeClient;

    public HomeController(ServiceClientFactory clientFactory) {
        this.homeClient = clientFactory.forService("inventory", "/api/v1/home");
    }

    @GetMapping("/{message}")
    @PreAuthorize("hasAuthority('x-bff:read')")
    public Mono<ResponseEntity<?>> sendMessage(@PathVariable String message) {
        return homeClient.get()
                .uri("/{message}", message)
                .retrieve()
                .bodyToMono(String.class)
                .map(XUtil::toJsonResponse);
    }
}
