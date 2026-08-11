package com.x.bff.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductImageUrlResolverTest {

    @Test
    void refreshesThumbnailVariantAndGalleryImageUrls() {
        ServiceClientFactory clientFactory = mock(ServiceClientFactory.class);
        WebClient storageClient = responseClient("https://bucket.s3.ap-southeast-1.amazonaws.com/media/fresh.webp");
        WebClient internalStorageClient = responseClient("https://bucket.s3.ap-southeast-1.amazonaws.com/media/fresh.webp");
        when(clientFactory.forService("storage", "/api/v1/files")).thenReturn(storageClient);
        when(clientFactory.forService("storage", "/internal/files")).thenReturn(internalStorageClient);

        String response = """
                {"status":1,"data":{"content":[{"thumbnail":"https://bucket.s3.ap-southeast-1.amazonaws.com/media/old.webp?X-Amz-Expires=86400","variants":[{"image":"/api/v1/files/1/content"}],"images":[{"imageUrl":"https://bucket.s3.ap-southeast-1.amazonaws.com/media/old.webp?X-Amz-Expires=86400"}]}]}}
                """;

        StepVerifier.create(new ProductImageUrlResolver(clientFactory, new ObjectMapper()).resolveResponse(response))
                .assertNext(resolved -> {
                    org.junit.jupiter.api.Assertions.assertEquals(3, count(resolved, "fresh.webp"));
                    org.junit.jupiter.api.Assertions.assertFalse(resolved.contains("old.webp"));
                })
                .verifyComplete();
    }

    private static WebClient responseClient(String url) {
        return WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .body("{\"status\":1,\"code\":200,\"data\":{\"id\":1,\"url\":\"" + url + "\"}}")
                        .build()))
                .build();
    }

    private static int count(String value, String token) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }
}
