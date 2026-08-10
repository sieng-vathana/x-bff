package com.x.bff.controller;

import com.sharedlib.response.ApiResponse;
import com.sharedlib.response.PageResponse;
import com.x.bff.dto.MarketplaceCategoryResponse;
import com.x.bff.dto.MarketplaceHomeResponse;
import com.x.bff.dto.MarketplaceProductResponse;
import com.x.bff.dto.MarketplaceProductView;
import com.x.bff.dto.MarketplaceStoreResponse;
import com.x.bff.dto.MarketplaceSummaryResponse;
import com.x.bff.service.ServiceClientFactory;
import com.x.bff.service.UserServiceClient;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/marketplace")
@Validated
public class MarketplaceController {
    private static final ParameterizedTypeReference<ApiResponse<PageResponse<MarketplaceProductResponse>>> PRODUCTS_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<PageResponse<MarketplaceCategoryResponse>>> CATEGORIES_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<List<MarketplaceStoreResponse>>> STORES_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<MarketplaceSummaryResponse>> SUMMARY_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient productClient;
    private final WebClient storeClient;
    private final WebClient customerClient;
    private final UserServiceClient userServiceClient;

    public MarketplaceController(ServiceClientFactory clientFactory, UserServiceClient userServiceClient) {
        this.productClient = clientFactory.forService("product", "/api/v1/marketplace");
        this.storeClient = clientFactory.forService("store", "/api/v1/stores");
        this.customerClient = clientFactory.forService("customer", "/internal/marketplace");
        this.userServiceClient = userServiceClient;
    }

    @GetMapping("/home")
    public Mono<ResponseEntity<ApiResponse<MarketplaceHomeResponse>>> getHome(
            @RequestParam(required = false) @Size(max = 120) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size) {
        Mono<PageResponse<MarketplaceProductResponse>> products = productClient.get()
                .uri(uri -> uri.path("/products")
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .queryParamIfPresent("search", Optional.ofNullable(search))
                        .build())
                .retrieve()
                .bodyToMono(PRODUCTS_TYPE)
                .map(ApiResponse::getData);

        Mono<PageResponse<MarketplaceCategoryResponse>> categories = productClient.get()
                .uri(uri -> uri.path("/categories")
                        .queryParam("page", 0)
                        .queryParam("size", 24)
                        .build())
                .retrieve()
                .bodyToMono(CATEGORIES_TYPE)
                .map(ApiResponse::getData);

        Mono<UserContext> user = currentUser().cache();
        Mono<MarketplaceSummaryResponse> summary = user.flatMap(this::loadSummary);
        return Mono.zip(products, categories, user, summary)
                .flatMap(tuple -> loadStores(tuple.getT1().content().stream()
                                .map(MarketplaceProductResponse::storeId)
                                .filter(id -> id != null)
                                .distinct()
                                .toList())
                        .map(stores -> toResponse(tuple.getT1(), tuple.getT2(), tuple.getT3(), tuple.getT4(), stores)))
                .map(home -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), home)));
    }

    private Mono<UserContext> currentUser() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(this::isAuthenticated)
                .flatMap(authentication -> userServiceClient.findByUsername(authentication.getName())
                        .map(user -> new UserContext(user.getId(), authentication.getName(), user.getFullName(), user.getEmail(), true))
                        .onErrorReturn(new UserContext(null, authentication.getName(), null, null, true)))
                .defaultIfEmpty(new UserContext(null, null, null, null, false));
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated();
    }

    private Mono<MarketplaceSummaryResponse> loadSummary(UserContext user) {
        if (user.id() == null) {
            return Mono.just(emptySummary());
        }
        return customerClient.get()
                .uri(uri -> uri.path("/summary").queryParam("userId", user.id()).build())
                .retrieve()
                .bodyToMono(SUMMARY_TYPE)
                .map(ApiResponse::getData)
                .onErrorReturn(emptySummary());
    }

    private Mono<List<MarketplaceStoreResponse>> loadStores(List<Long> ids) {
        if (ids.isEmpty()) {
            return Mono.just(List.of());
        }
        return storeClient.get()
                .uri(uri -> {
                    uri.path("/public");
                    ids.forEach(id -> uri.queryParam("ids", id));
                    return uri.build();
                })
                .retrieve()
                .bodyToMono(STORES_TYPE)
                .map(ApiResponse::getData)
                .defaultIfEmpty(List.of());
    }

    private MarketplaceHomeResponse toResponse(
            PageResponse<MarketplaceProductResponse> products,
            PageResponse<MarketplaceCategoryResponse> categories,
            UserContext user,
            MarketplaceSummaryResponse summary,
            List<MarketplaceStoreResponse> stores) {
        Map<Long, MarketplaceStoreResponse> storesById = stores.stream()
                .collect(Collectors.toMap(MarketplaceStoreResponse::id, Function.identity(), (left, right) -> left));
        PageResponse<MarketplaceProductView> productViews = new PageResponse<>(
                products.content().stream().map(product -> new MarketplaceProductView(
                        product.productId(), product.productName(), product.shortName(), product.thumbnail(),
                        product.description(), product.categoryId(), product.categoryName(), product.currencyCode(),
                        product.onlinePrice(), product.compareAtPrice(), product.quantity(), product.featured(),
                        storesById.get(product.storeId()))).toList(),
                products.page(), products.size(), products.totalElements(), products.totalPages(), products.hasNext());
        return new MarketplaceHomeResponse(
                productViews,
                categories.content(),
                new MarketplaceHomeResponse.AccountSummary(
                        user.authenticated(), user.id(), user.username(),
                        summary != null && summary.customer() != null
                                ? summary.customer().fullName() : user.fullName(),
                        summary != null && summary.customer() != null
                                ? summary.customer().email() : user.email()),
                new MarketplaceHomeResponse.FavoriteSummary(
                        summary == null ? 0 : summary.favoriteCount(),
                        summary == null ? List.of() : summary.favoriteProductIds()),
                new MarketplaceHomeResponse.CartSummary(
                        summary == null ? 0 : summary.cartItemCount(),
                        summary == null ? BigDecimal.ZERO : summary.cartSubtotal(),
                        summary == null ? null : summary.cartCurrency(),
                        summary == null ? List.of() : summary.cartItems()));
    }

    private MarketplaceSummaryResponse emptySummary() {
        return new MarketplaceSummaryResponse(null, List.of(), 0, 0, BigDecimal.ZERO, null, List.of());
    }

    private record UserContext(Long id, String username, String fullName, String email, boolean authenticated) {
    }
}
