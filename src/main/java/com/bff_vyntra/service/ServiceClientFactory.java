package com.bff_vyntra.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class ServiceClientFactory {

    private final WebClient.Builder webClientBuilder;
    private final String gatewayUrl;

    public ServiceClientFactory(
            WebClient.Builder webClientBuilder,
            @Value("${api.gateway.url}") String gatewayUrl) {
        this.webClientBuilder = webClientBuilder;
        this.gatewayUrl = gatewayUrl;
    }

    /**
     * Creates a WebClient configured for a specific downstream service path.
     * Example: forService("/api/v1/orders")
     */
    public WebClient forService(String servicePath) {
        return webClientBuilder
                .baseUrl(gatewayUrl + servicePath)
                .build();
    }
}
