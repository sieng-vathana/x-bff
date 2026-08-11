package com.x.bff.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sharedlib.response.ApiResponse;
import com.sharedlib.response.PageResponse;
import com.x.bff.dto.MarketplaceProductResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves legacy and expired product image URLs to fresh storage URLs. */
@Service
public class ProductImageUrlResolver {

    private static final Logger log = LoggerFactory.getLogger(ProductImageUrlResolver.class);
    private static final Pattern FILE_PROXY_URL = Pattern.compile("^/api/v1/files/(\\d+)/content(?:\\?.*)?$");
    private static final Pattern S3_OBJECT_URL = Pattern.compile(
            "^https://[^/]+\\.s3(?:[.-][^/]+)?\\.amazonaws\\.com/([^?]+)(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    private final WebClient storageClient;
    private final WebClient internalStorageClient;
    private final ObjectMapper objectMapper;

    public ProductImageUrlResolver(ServiceClientFactory clientFactory, ObjectMapper objectMapper) {
        this.storageClient = clientFactory.forService("storage", "/api/v1/files");
        this.internalStorageClient = clientFactory.forService("storage", "/internal/files");
        this.objectMapper = objectMapper;
    }

    /**
     * Rewrites only product image fields inside a successful product API response.
     * A missing storage record leaves the original value intact so one old image
     * cannot make the whole product table fail.
     */
    public Mono<String> resolveResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return Mono.just(rawResponse);
        }

        final JsonNode root;
        try {
            root = objectMapper.readTree(rawResponse);
        } catch (JsonProcessingException exception) {
            return Mono.just(rawResponse);
        }

        List<ImageField> fields = new ArrayList<>();
        collectProductImageFields(root, fields);
        if (fields.isEmpty()) {
            return Mono.just(rawResponse);
        }

        return Flux.fromIterable(fields)
                .concatMap(field -> resolve(field.value())
                        .doOnNext(url -> field.parent().put(field.name(), url)))
                .then(Mono.fromCallable(() -> objectMapper.writeValueAsString(root)))
                .onErrorResume(exception -> {
                    log.warn("Unable to refresh product image URLs; returning the original response", exception);
                    return Mono.just(rawResponse);
                });
    }

    public Mono<PageResponse<MarketplaceProductResponse>> resolveMarketplacePage(
            PageResponse<MarketplaceProductResponse> page) {
        if (page == null || page.content() == null || page.content().isEmpty()) {
            return Mono.just(page);
        }
        return Flux.fromIterable(page.content())
                .concatMap(product -> {
                    if (product.thumbnail() == null || product.thumbnail().isBlank()) {
                        return Mono.just(product);
                    }
                    return resolve(product.thumbnail())
                            .map(thumbnail -> new MarketplaceProductResponse(
                                    product.productId(), product.storeId(), product.productName(), product.shortName(),
                                    thumbnail, product.description(), product.categoryId(), product.categoryName(),
                                    product.currencyCode(), product.onlinePrice(), product.compareAtPrice(),
                                    product.quantity(), product.featured()))
                            .defaultIfEmpty(product);
                })
                .collectList()
                .map(content -> new PageResponse<>(
                        content, page.page(), page.size(), page.totalElements(), page.totalPages(), page.hasNext()));
    }

    private void collectProductImageFields(JsonNode root, List<ImageField> fields) {
        JsonNode data = root.isObject() ? root.get("data") : null;
        if (data == null || data.isNull()) {
            return;
        }

        if (data.isObject() && data.has("content") && data.get("content").isArray()) {
            data.get("content").forEach(product -> collectProductImageFieldsFromProduct(product, fields));
        } else {
            collectProductImageFieldsFromProduct(data, fields);
        }
    }

    private void collectProductImageFieldsFromProduct(JsonNode product, List<ImageField> fields) {
        if (!product.isObject()) {
            return;
        }

        addTextField(product, "thumbnail", fields);
        collectNestedImageFields(product.get("variants"), "image", fields);
        collectNestedImageFields(product.get("images"), "imageUrl", fields);
    }

    private void collectNestedImageFields(JsonNode items, String fieldName, List<ImageField> fields) {
        if (items == null || !items.isArray()) {
            return;
        }
        items.forEach(item -> addTextField(item, fieldName, fields));
    }

    private void addTextField(JsonNode node, String fieldName, List<ImageField> fields) {
        if (node instanceof ObjectNode objectNode) {
            JsonNode value = objectNode.get(fieldName);
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                fields.add(new ImageField(objectNode, fieldName, value.asText()));
            }
        }
    }

    private Mono<String> resolve(String imageUrl) {
        String normalized = imageUrl.trim();
        Matcher fileReference = FILE_PROXY_URL.matcher(normalized);
        if (fileReference.matches()) {
            return resolveById(Long.valueOf(fileReference.group(1))).onErrorReturn(imageUrl);
        }

        Matcher s3Object = S3_OBJECT_URL.matcher(normalized);
        if (s3Object.matches()) {
            return resolveByRelativePath(s3Object.group(1)).onErrorReturn(imageUrl);
        }

        return Mono.just(imageUrl);
    }

    private Mono<String> resolveById(Long id) {
        return storageClient.get()
                .uri("/{id}", id)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<StoredFileResponse>>() {})
                .map(ApiResponse::getData)
                .map(StoredFileResponse::url);
    }

    private Mono<String> resolveByRelativePath(String relativePath) {
        return internalStorageClient.get()
                .uri("/by-relative-path?relativePath={relativePath}", relativePath)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<StoredFileResponse>>() {})
                .map(ApiResponse::getData)
                .map(StoredFileResponse::url);
    }

    private record ImageField(ObjectNode parent, String name, String value) {
    }

    private record StoredFileResponse(Long id, String url) {
    }
}
