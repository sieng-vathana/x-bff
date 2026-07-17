package com.bff_vyntra.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class ServiceClientFactory {

    private final WebClient.Builder webClientBuilder;
    private final Environment environment;
    private final String gatewayUrl;

    public ServiceClientFactory(
            WebClient.Builder webClientBuilder,
            Environment environment,
            @Value("${api.gateway.url:}") String gatewayUrl) {
        this.webClientBuilder = webClientBuilder;
        this.environment = environment;
        this.gatewayUrl = gatewayUrl;
    }

    /**
     * Creates a client for an internal domain service. A configured
     * services.&lt;name&gt;.base-url points directly to the service, as it does in
     * Kubernetes. The API Gateway remains a fallback for existing local
     * configuration.
     */
    public WebClient forService(String serviceName, String servicePath) {
        String baseUrl = resolveServiceBaseUrl(serviceName, servicePath);

        return webClientBuilder.clone()
                .baseUrl(baseUrl)
                .filter(propagateBearerToken())
                .build();
    }

    String resolveServiceBaseUrl(String serviceName, String servicePath) {
        String normalizedServicePath = servicePath.startsWith("/")
                ? servicePath
                : "/" + servicePath;
        String serviceUrl = environment.getProperty("services." + serviceName + ".base-url");

        if (StringUtils.hasText(serviceUrl)) {
            return stripTrailingSlash(serviceUrl) + normalizedServicePath;
        }
        if (StringUtils.hasText(gatewayUrl)) {
            return stripTrailingSlash(gatewayUrl) + normalizedServicePath;
        }

        throw new IllegalStateException("No base URL configured for service '" + serviceName + "'");
    }

    private String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private ExchangeFilterFunction propagateBearerToken() {
        return (request, next) -> ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication().getCredentials())
                .ofType(String.class)
                .filter(token -> !token.isBlank())
                .map(token -> ClientRequest.from(request)
                        .headers(headers -> headers.setBearerAuth(token))
                        .build())
                .defaultIfEmpty(request)
                .flatMap(next::exchange);
    }
}
