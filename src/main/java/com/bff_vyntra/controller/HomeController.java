package com.bff_vyntra.controller;

import com.bff_vyntra.service.ServiceClientFactory;
import com.bff_vyntra.utils.VyntraUtil;
import org.springframework.http.ResponseEntity;
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
    public Mono<ResponseEntity<?>> sendMessage(@PathVariable String message) {
        return homeClient.get()
                .uri("/{message}", message)
                .retrieve()
                .bodyToMono(String.class)
                .map(VyntraUtil::toJsonResponse);
    }
}
