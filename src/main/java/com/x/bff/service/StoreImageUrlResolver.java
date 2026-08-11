package com.x.bff.service;

import com.x.bff.dto.StoreImageRequest;
import com.x.bff.dto.StoreImageResponse;
import com.x.bff.dto.MarketplaceStoreResponse;
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
    private static final Pattern S3_OBJECT_URL = Pattern.compile(
            "^https://[^/]+\\.s3(?:[.-][^/]+)?\\.amazonaws\\.com/([^?]+)(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    private final WebClient storageClient;
    private final WebClient internalStorageClient;

    public StoreImageUrlResolver(ServiceClientFactory clientFactory) {
        this.storageClient = clientFactory.forService("storage", "/api/v1/files");
        this.internalStorageClient = clientFactory.forService("storage", "/internal/files");
    }

    public Mono<List<StoreImageRequest>> resolveRequests(List<StoreImageRequest> images) {
        return Flux.fromIterable(images)
                .concatMap(image -> resolve(image.imageUrl())
                        .map(file -> new StoreImageRequest(
                                file.id() == null ? image.imageUrl() : fileReference(file.id()),
                                image.isPrimary(), image.sortOrder())))
                .collectList();
    }

    public Mono<StoreResponse> resolveResponse(StoreResponse store) {
        if (store.images() == null || store.images().isEmpty()) {
            return Mono.just(store);
        }
        return Flux.fromIterable(store.images())
                .concatMap(image -> resolve(image.imageUrl())
                        .map(file -> new StoreImageResponse(
                                image.id(), file.url(), file.id() == null ? image.fileId() : file.id(),
                                image.isPrimary(), image.sortOrder()))
                        .onErrorResume(ex -> Mono.just(image)))
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

    /**
     * Marketplace store cards are public, so they must expose browser-ready
     * storage URLs instead of the authenticated BFF file proxy path.
     */
    public Mono<List<MarketplaceStoreResponse>> resolveMarketplaceStores(List<MarketplaceStoreResponse> stores) {
        if (stores == null || stores.isEmpty()) {
            return Mono.just(List.of());
        }
        return Flux.fromIterable(stores)
                .concatMap(store -> resolve(store.image())
                        .map(file -> new MarketplaceStoreResponse(
                                store.id(), store.name(), store.code(), store.city(), store.countryCode(), file.url())))
                .collectList();
    }

    private Mono<ResolvedImageUrl> resolve(String imageUrl) {
        String normalized = imageUrl == null ? "" : imageUrl.trim();
        Matcher fileReference = FILE_PROXY_URL.matcher(normalized);
        if (fileReference.matches()) {
            return resolveById(Long.valueOf(fileReference.group(1)));
        }
        Matcher s3Object = S3_OBJECT_URL.matcher(normalized);
        if (s3Object.matches()) {
            return resolveByRelativePath(s3Object.group(1));
        }
        return Mono.just(new ResolvedImageUrl(imageUrl, null));
    }

    private Mono<ResolvedImageUrl> resolveById(Long id) {
        return storageClient.get()
                .uri("/{id}", id)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<StoredFileResponse>>() {})
                .map(ApiResponse::getData)
                .filter(java.util.Objects::nonNull)
                .map(file -> new ResolvedImageUrl(file.url(), file.id()));
    }

    private Mono<ResolvedImageUrl> resolveByRelativePath(String relativePath) {
        return internalStorageClient.get()
                .uri("/by-relative-path?relativePath={relativePath}", relativePath)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<StoredFileResponse>>() {})
                .map(ApiResponse::getData)
                .map(file -> new ResolvedImageUrl(file.url(), file.id()));
    }

    private static String fileReference(Long fileId) {
        return "/api/v1/files/" + fileId + "/content";
    }

    private record StoredFileResponse(Long id, String url) {
    }

    private record ResolvedImageUrl(String url, Long id) {
    }
}
