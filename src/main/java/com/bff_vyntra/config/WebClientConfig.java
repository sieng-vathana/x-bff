package com.bff_vyntra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    private final String gatewayUrl;

    // Move the @Value annotation inside the constructor parameters here:
    public WebClientConfig(@Value("${api.gateway.url}") String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
    }

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public WebClient gatewayWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(gatewayUrl)
                .build();
    }
}