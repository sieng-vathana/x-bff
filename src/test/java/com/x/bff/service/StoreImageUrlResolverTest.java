package com.x.bff.service;

import com.x.bff.dto.StoreImageResponse;
import com.x.bff.dto.StoreResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoreImageUrlResolverTest {

    @Test
    void replacesLegacyFileProxyUrlWithTheStorageAwsUrl() {
        ServiceClientFactory clientFactory = mock(ServiceClientFactory.class);
        WebClient storageClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .body("""
                                {"status":1,"code":200,"data":{"id":1,"url":"https://bucket.s3.ap-southeast-1.amazonaws.com/media/store.webp?signature=abc"}}
                                """)
                        .build()))
                .build();
        when(clientFactory.forService("storage", "/api/v1/files")).thenReturn(storageClient);

        StoreResponse store = new StoreResponse(
                13L, 12L, "Shopify", "MAIN", "123 Street", null, null, "Phnom Penh", null, "KH",
                null, null, null, null, null, null, null,
                List.of(new StoreImageResponse(1L, "/api/v1/files/1/content", null, true, 0)), 1, null, null);

        StepVerifier.create(new StoreImageUrlResolver(clientFactory).resolveResponse(store))
                .assertNext(resolved -> {
                    org.junit.jupiter.api.Assertions.assertEquals(
                            "https://bucket.s3.ap-southeast-1.amazonaws.com/media/store.webp?signature=abc",
                            resolved.images().get(0).imageUrl());
                    org.junit.jupiter.api.Assertions.assertEquals(1L, resolved.images().get(0).fileId());
                })
                .verifyComplete();
    }

    @Test
    void replacesAnOldRawS3UrlWithAFreshSignedUrl() {
        ServiceClientFactory clientFactory = mock(ServiceClientFactory.class);
        WebClient internalStorageClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .body("""
                                {"status":1,"code":200,"data":{"id":1,"url":"https://bucket.s3.ap-southeast-1.amazonaws.com/media/store.webp?X-Amz-Signature=fresh"}}
                                """)
                        .build()))
                .build();
        when(clientFactory.forService("storage", "/api/v1/files")).thenReturn(WebClient.builder().build());
        when(clientFactory.forService("storage", "/internal/files")).thenReturn(internalStorageClient);

        StoreResponse store = new StoreResponse(
                13L, 12L, "Shopify", "MAIN", "123 Street", null, null, "Phnom Penh", null, "KH",
                null, null, null, null, null, null, null,
                List.of(new StoreImageResponse(1L,
                        "https://bucket.s3.ap-southeast-1.amazonaws.com/media/store.webp", null, true, 0)),
                1, null, null);

        StepVerifier.create(new StoreImageUrlResolver(clientFactory).resolveResponse(store))
                .assertNext(resolved -> {
                    org.junit.jupiter.api.Assertions.assertTrue(
                            resolved.images().get(0).imageUrl().contains("X-Amz-Signature=fresh"));
                    org.junit.jupiter.api.Assertions.assertEquals(1L, resolved.images().get(0).fileId());
                })
                .verifyComplete();
    }
}
