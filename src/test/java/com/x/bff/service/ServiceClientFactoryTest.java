package com.x.bff.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceClientFactoryTest {

    @Test
    void usesDirectServiceUrlWhenConfigured() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("services.product.base-url", "http://product-service:8082/");
        ServiceClientFactory factory = new ServiceClientFactory(
                WebClient.builder(), environment, "http://api-gateway:8080");

        assertThat(factory.resolveServiceBaseUrl("product", "/api/v1/products"))
                .isEqualTo("http://product-service:8082/api/v1/products");
    }

    @Test
    void fallsBackToGatewayForExistingLocalConfiguration() {
        ServiceClientFactory factory = new ServiceClientFactory(
                WebClient.builder(), new MockEnvironment(), "http://localhost:8080/");

        assertThat(factory.resolveServiceBaseUrl("order", "/api/v1/orders"))
                .isEqualTo("http://localhost:8080/api/v1/orders");
    }

    @Test
    void rejectsMissingServiceConfiguration() {
        ServiceClientFactory factory = new ServiceClientFactory(
                WebClient.builder(), new MockEnvironment(), "");

        assertThatThrownBy(() -> factory.resolveServiceBaseUrl("shop", "/api/v1/shops"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shop");
    }
}
