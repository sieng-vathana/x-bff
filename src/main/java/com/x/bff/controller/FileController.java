package com.x.bff.controller;

import com.x.bff.service.ServiceClientFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final WebClient storageClient;

    public FileController(ServiceClientFactory clientFactory) {
        this.storageClient = clientFactory.forService("storage", "/api/v1/files");
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<String>> upload(
            @RequestPart("file") FilePart file,
            @RequestParam(value = "ownerType", required = false) String ownerType,
            @RequestParam(value = "ownerId", required = false) String ownerId) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.asyncPart("file", file.content(), DataBuffer.class)
                .filename(file.filename())
                .contentType(file.headers().getContentType() != null
                        ? file.headers().getContentType()
                        : MediaType.APPLICATION_OCTET_STREAM);
        if (ownerType != null) {
            builder.part("ownerType", ownerType);
        }
        if (ownerId != null) {
            builder.part("ownerId", ownerId);
        }

        return storageClient.post()
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .toEntity(String.class);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<String>> meta(@PathVariable Long id) {
        return storageClient.get()
                .uri("/{id}", id)
                .retrieve()
                .toEntity(String.class);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable Long id) {
        return storageClient.delete()
                .uri("/{id}", id)
                .retrieve()
                .toBodilessEntity();
    }
}
