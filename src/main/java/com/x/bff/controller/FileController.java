package com.x.bff.controller;

import com.x.bff.service.ServiceClientFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Proxies file APIs to x-storage-service.
 * Upload forwards the raw multipart body (with original boundary) — does not re-parse form-data.
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final WebClient storageClient;

    public FileController(ServiceClientFactory clientFactory) {
        this.storageClient = clientFactory.forService("storage", "/api/v1/files");
    }

    /**
     * Stream multipart upload to storage. Body is not re-encoded.
     * Postman: form-data field {@code file} (File) + optional kind/ownerType/ownerId.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('x-storage:upload')")
    public Mono<ResponseEntity<String>> upload(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        MediaType contentType = request.getHeaders().getContentType();
        Flux<DataBuffer> body = request.getBody();

        WebClient.RequestBodySpec spec = storageClient.post();
        if (contentType != null) {
            // Keep full Content-Type including boundary=...
            spec = spec.header(HttpHeaders.CONTENT_TYPE, contentType.toString());
        }
        long contentLength = request.getHeaders().getContentLength();
        if (contentLength >= 0) {
            spec = spec.header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength));
        }

        return spec
                .body(BodyInserters.fromDataBuffers(body))
                .retrieve()
                .toEntity(String.class);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('x-storage:read')")
    public Mono<ResponseEntity<String>> meta(@PathVariable Long id) {
        return storageClient.get()
                .uri("/{id}", id)
                .retrieve()
                .toEntity(String.class);
    }

    /**
     * Stream file bytes from storage (S3/local) for display or download.
     * Use this URL in {@code <img src="...">} after login cookie is set (same origin / credentials).
     */
    @GetMapping("/{id}/content")
    @PreAuthorize("hasAuthority('x-storage:read')")
    public Mono<ResponseEntity<Flux<DataBuffer>>> content(@PathVariable Long id) {
        // toEntityFlux keeps the storage response open until its bytes are written. Reading the body
        // inside exchangeToMono did not: the response was released first and images arrived empty.
        return storageClient.get()
                .uri("/{id}/content", id)
                .retrieve()
                .toEntityFlux(DataBuffer.class)
                .map(response -> {
                    HttpHeaders upstream = response.getHeaders();
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(upstream.getContentType() != null ? upstream.getContentType() : MediaType.APPLICATION_OCTET_STREAM);
                    if (upstream.getContentLength() >= 0) {
                        headers.setContentLength(upstream.getContentLength());
                    }
                    upstream.getOrEmpty(HttpHeaders.CONTENT_DISPOSITION).forEach(v -> headers.add(HttpHeaders.CONTENT_DISPOSITION, v));
                    // A file id always has the same bytes, so the browser may keep it for a day.
                    headers.setCacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate());
                    return ResponseEntity.status(response.getStatusCode()).headers(headers).body(response.getBody());
                });
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('x-storage:delete')")
    public Mono<ResponseEntity<Void>> delete(@PathVariable Long id) {
        return storageClient.delete()
                .uri("/{id}", id)
                .retrieve()
                .toBodilessEntity();
    }
}
