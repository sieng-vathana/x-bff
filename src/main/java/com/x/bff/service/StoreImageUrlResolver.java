package com.x.bff.service;

import com.x.bff.dto.StoreImageRequest;
import com.x.bff.dto.StoreImageResponse;
import com.x.bff.dto.StoreResponse;
import com.sharedlib.response.ApiResponse;
import com.sharedlib.response.PageResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Replaces legacy BFF file-proxy URLs with the storage service's browser-ready AWS URL. */
@Service
public class StoreImageUrlResolver {

    private static final Pattern FILE_PROXY_URL = Pattern.compile("^/api/v1/files/(\\d+)/content(?:\\?.*)?$");

    private final WebClient storageClient;

    public StoreImageUrlResolver(ServiceClientFactory clientFactory) {
        this.storageClient = clientFactory.forService("storage", "/api/v1/files");
    }

    public Mono<List<StoreImageRequest>> resolveRequests(List<StoreImageRequest> images) {
        return Flux.fromIterable(images)
                .concatMap(image -> resolve(image.imageUrl())
                        .map(url -> new StoreImageRequest(url, image.isPrimary(), image.sortOrder())))
                .collectList();
    }

    public Mono<StoreResponse> resolveResponse(StoreResponse store) {
        if (store.images() == null || store.images().isEmpty()) {
            return Mono.just(store);
        }
        return Flux.fromIterable(store.images())
                .concatMap(image -> resolve(image.imageUrl())
                        .map(url -> new StoreImageResponse(image.id(), url, image.isPrimary(), image.sortOrder())))
                .collectList()
                .map(images -> new StoreResponse(
                        store.id(), store.businessId(), store.name(), store.code(), store.addressLine1(),
                        store.addressLine2(), store.landmark(), store.city(), store.stateProvince(), store.countryCode(),
                        store.postalCode(), store.phone(), store.alternatePhone(), store.email(), store.website(),
                        store.latitude(), store.longitude(), images, store.status(), store.createdAt(), store.updatedAt()));
    }

    public Mono<PageResponse<StoreResponse>> resolvePage(PageResponse<StoreResponse> page) {
        if (page == null || page.content() == null || page.content().isEmpty()) {
            return Mono.just(page);
        }
        return Flux.fromIterable(page.content())
                .concatMap(this::resolveResponse)
                .collectList()
                .map(content -> new PageResponse<>(
                        content, page.page(), page.size(), page.totalElements(), page.totalPages(), page.hasNext()));
    }

    private Mono<String> resolve(String imageUrl) {
        Matcher matcher = FILE_PROXY_URL.matcher(imageUrl == null ? "" : imageUrl.trim());
        if (!matcher.matches()) {
            return Mono.just(imageUrl);
        }
        return storageClient.get()
                .uri("/{id}", Long.valueOf(matcher.group(1)))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<StoredFileResponse>>() {})
                .map(ApiResponse::getData)
                .map(StoredFileResponse::url);
    }

    private record StoredFileResponse(Long id, String url) {
    }
}
