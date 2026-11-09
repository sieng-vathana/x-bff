package com.x.bff.controller;

import com.x.bff.service.MarketplaceImageSigner;
import com.x.bff.service.ServiceClientFactory;
import jakarta.validation.constraints.Positive;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Public image route for the marketplace. Only links signed by {@link MarketplaceImageSigner}
 * are served, and only image content types, so shoppers can see product and store photos
 * without a session while other stored files stay private.
 */
@Validated
@RestController
@RequestMapping("/api/v1/marketplace/images")
public class MarketplaceImageController {

    private final MarketplaceImageSigner imageSigner;
    private final org.springframework.web.reactive.function.client.WebClient storageClient;

    public MarketplaceImageController(ServiceClientFactory clientFactory, MarketplaceImageSigner imageSigner) {
        this.imageSigner = imageSigner;
        this.storageClient = clientFactory.forService("storage", "/api/v1/files");
    }

    @GetMapping("/{fileId}")
    public Mono<ResponseEntity<Flux<DataBuffer>>> image(@PathVariable @Positive Long fileId,
                                                        @RequestParam(required = false) String sig) {
        if (!imageSigner.verify(fileId, sig)) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found"));
        }
        return storageClient.get()
                .uri("/{id}/content", fileId)
                .retrieve()
                .toEntityFlux(DataBuffer.class)
                .flatMap(response -> {
                    MediaType contentType = response.getHeaders().getContentType();
                    if (contentType == null || !"image".equalsIgnoreCase(contentType.getType())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found"));
                    }
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(contentType);
                    if (response.getHeaders().getContentLength() >= 0) {
                        headers.setContentLength(response.getHeaders().getContentLength());
                    }
                    // A file id always has the same bytes, and the image is public, so shared caches may keep it.
                    headers.setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic());
                    return Mono.just(ResponseEntity.ok().headers(headers).body(response.getBody()));
                });
    }
}
