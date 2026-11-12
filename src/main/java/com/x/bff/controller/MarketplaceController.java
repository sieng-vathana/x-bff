package com.x.bff.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sharedlib.response.ApiResponse;
import com.sharedlib.response.PageResponse;
import com.x.bff.dto.MarketplaceCartItemRequest;
import com.x.bff.dto.MarketplaceCategoryResponse;
import com.x.bff.dto.MarketplaceCheckoutRequest;
import com.x.bff.dto.MarketplaceDeliveryQuoteRequest;
import com.x.bff.dto.MarketplaceDeliveryQuoteResponse;
import com.x.bff.dto.MarketplaceFavoriteRequest;
import com.x.bff.dto.MarketplaceFavoriteResponse;
import com.x.bff.dto.MarketplaceGuestCheckoutRequest;
import com.x.bff.dto.MarketplaceGuestOrderResponse;
import com.x.bff.dto.MarketplaceHomeResponse;
import com.x.bff.dto.MarketplaceProductDetailResponse;
import com.x.bff.dto.MarketplaceProductResponse;
import com.x.bff.dto.MarketplaceProductView;
import com.x.bff.dto.MarketplaceQuantityRequest;
import com.x.bff.dto.MarketplaceStoreResponse;
import com.x.bff.dto.MarketplaceSummaryResponse;
import com.x.bff.service.GuestOrderTokens;
import com.x.bff.service.ServiceClientFactory;
import com.x.bff.service.ProductImageUrlResolver;
import com.x.bff.service.StoreImageUrlResolver;
import com.x.bff.service.UserServiceClient;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/marketplace")
@Validated
public class MarketplaceController {
    private static final String GUEST_SERVICE_USER = "marketplace-guest-checkout";
    /** Header carrying the access token a guest got back from checkout. */
    public static final String GUEST_TOKEN_HEADER = "X-Guest-Order-Token";

    private static final ParameterizedTypeReference<ApiResponse<PageResponse<MarketplaceProductResponse>>> PRODUCTS_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<PageResponse<MarketplaceCategoryResponse>>> CATEGORIES_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<List<MarketplaceStoreResponse>>> STORES_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<PageResponse<MarketplaceStoreResponse>>> STORE_PAGE_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<MarketplaceSummaryResponse>> SUMMARY_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<MarketplaceProductDetailResponse>> PRODUCT_DETAIL_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<MarketplaceStoreResponse>> STORE_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<List<MarketplaceDeliveryQuoteResponse>>> QUOTES_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<List<MarketplaceFavoriteResponse>>> FAVORITES_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<PageResponse<JsonNode>>> ORDER_PAGE_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<JsonNode>> ORDER_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<List<JsonNode>>> PAYMENT_LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient productClient;
    private final WebClient storeClient;
    private final WebClient businessClient;
    private final WebClient customerClient;
    private final WebClient deliveryClient;
    private final WebClient orderClient;
    private final WebClient paymentClient;
    private final UserServiceClient userServiceClient;
    private final com.x.bff.security.JwtUtils jwtUtils;
    private final ProductImageUrlResolver productImageUrlResolver;
    private final StoreImageUrlResolver storeImageUrlResolver;
    private final GuestOrderTokens guestOrderTokens;

    public MarketplaceController(
            ServiceClientFactory clientFactory,
            UserServiceClient userServiceClient,
            ProductImageUrlResolver productImageUrlResolver,
            StoreImageUrlResolver storeImageUrlResolver,
            GuestOrderTokens guestOrderTokens,
            com.x.bff.security.JwtUtils jwtUtils) {
        this.productClient = clientFactory.forService("product", "/api/v1/marketplace");
        this.storeClient = clientFactory.forService("store", "/api/v1/stores");
        this.businessClient = clientFactory.forService("business", "/api/v1/businesses");
        this.customerClient = clientFactory.forService("customer", "/internal/marketplace");
        this.deliveryClient = clientFactory.forService("delivery", "/api/v1/deliveries");
        this.orderClient = clientFactory.forService("order", "/api/v1/orders");
        this.paymentClient = clientFactory.forService("payment", "/api/v1/payments");
        this.userServiceClient = userServiceClient;
        this.productImageUrlResolver = productImageUrlResolver;
        this.storeImageUrlResolver = storeImageUrlResolver;
        this.guestOrderTokens = guestOrderTokens;
        this.jwtUtils = jwtUtils;
    }

    @GetMapping("/categories")
    public Mono<ResponseEntity<ApiResponse<PageResponse<MarketplaceCategoryResponse>>>> getCategories(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int size) {
        return productClient.get()
                .uri(uri -> uri.path("/categories").queryParam("page", page).queryParam("size", size).build())
                .retrieve()
                .bodyToMono(CATEGORIES_TYPE)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/products")
    public Mono<ResponseEntity<ApiResponse<PageResponse<MarketplaceProductView>>>> getProducts(
            @RequestParam(required = false) @Size(max = 120) String search,
            @RequestParam(required = false) @Positive Long categoryId,
            @RequestParam(required = false) @Positive Long storeId,
            @RequestParam(required = false) Boolean featuredOnly,
            @RequestParam(required = false) @jakarta.validation.constraints.DecimalMin("0.00") BigDecimal minPrice,
            @RequestParam(required = false) @jakarta.validation.constraints.DecimalMin("0.00") BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            return Mono.error(new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "minPrice must be less than or equal to maxPrice"));
        }
        return productClient.get()
                .uri(uri -> uri.path("/products")
                        .queryParamIfPresent("search", Optional.ofNullable(search))
                        .queryParamIfPresent("categoryId", Optional.ofNullable(categoryId))
                        .queryParamIfPresent("storeId", Optional.ofNullable(storeId))
                        .queryParamIfPresent("featuredOnly", Optional.ofNullable(featuredOnly))
                        .queryParamIfPresent("minPrice", Optional.ofNullable(minPrice))
                        .queryParamIfPresent("maxPrice", Optional.ofNullable(maxPrice))
                        .queryParamIfPresent("inStock", Optional.ofNullable(inStock))
                        .queryParam("sort", sort).queryParam("page", page).queryParam("size", size).build())
                .retrieve()
                .bodyToMono(PRODUCTS_TYPE)
                .map(ApiResponse::getData)
                .flatMap(this::enrichProductPage)
                .map(result -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), result)));
    }

    @GetMapping("/products/{productId}")
    public Mono<ResponseEntity<ApiResponse<MarketplaceProductDetailResponse>>> getProduct(
            @PathVariable @Positive Long productId,
            @RequestParam(required = false) @Positive Long storeId) {
        return fetchProductDetail(productId, storeId)
                .flatMap(product -> publicStore(product.storeId())
                        .map(store -> new MarketplaceProductDetailResponse(
                                product.productId(), product.storeId(), product.productName(), product.shortName(),
                                product.description(), product.thumbnail(), product.categoryId(), product.categoryName(),
                                product.currencyCode(), product.featured(), product.images(), product.variants(), store)))
                .map(result -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), result)));
    }

    @GetMapping("/stores")
    public Mono<ResponseEntity<ApiResponse<PageResponse<MarketplaceStoreResponse>>>> getStores(
            @RequestParam(required = false) @Size(max = 120) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size) {
        return storeClient.get()
                .uri(uri -> uri.path("/public/directory").queryParam("page", page).queryParam("size", size)
                        .queryParamIfPresent("search", Optional.ofNullable(search)).build())
                .retrieve()
                .bodyToMono(STORE_PAGE_TYPE)
                .map(ApiResponse::getData)
                .flatMap(this::enrichStorePage)
                .map(result -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), result)));
    }

    @GetMapping("/stores/{storeId}")
    public Mono<ResponseEntity<ApiResponse<MarketplaceStoreResponse>>> getStore(
            @PathVariable @Positive Long storeId) {
        return publicStore(storeId)
                .map(store -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), store)));
    }

    @GetMapping("/stores/{storeId}/products")
    public Mono<ResponseEntity<ApiResponse<PageResponse<MarketplaceProductView>>>> getStoreProducts(
            @PathVariable @Positive Long storeId,
            @RequestParam(required = false) @Size(max = 120) String search,
            @RequestParam(required = false) @Positive Long categoryId,
            @RequestParam(required = false) @jakarta.validation.constraints.DecimalMin("0.00") BigDecimal minPrice,
            @RequestParam(required = false) @jakarta.validation.constraints.DecimalMin("0.00") BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size) {
        return publicStore(storeId).then(productClient.get()
                .uri(uri -> uri.path("/products").queryParam("storeId", storeId)
                        .queryParamIfPresent("search", Optional.ofNullable(search))
                        .queryParamIfPresent("categoryId", Optional.ofNullable(categoryId))
                        .queryParamIfPresent("minPrice", Optional.ofNullable(minPrice))
                        .queryParamIfPresent("maxPrice", Optional.ofNullable(maxPrice))
                        .queryParamIfPresent("inStock", Optional.ofNullable(inStock))
                        .queryParam("sort", sort).queryParam("page", page).queryParam("size", size).build())
                .retrieve().bodyToMono(PRODUCTS_TYPE).map(ApiResponse::getData)
                .flatMap(this::enrichProductPage)
                .map(result -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), result))));
    }

    @GetMapping("/home")
    public Mono<ResponseEntity<ApiResponse<MarketplaceHomeResponse>>> getHome(
            @RequestParam(required = false) @Size(max = 120) String search,
            @RequestParam(required = false) @Positive Long categoryId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "0") @Min(0) int storePage,
            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int storeSize) {
        Mono<PageResponse<MarketplaceProductResponse>> products = productClient.get()
                .uri(uri -> uri.path("/products")
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .queryParamIfPresent("categoryId", Optional.ofNullable(categoryId))
                        .queryParamIfPresent("search", Optional.ofNullable(search))
                        .build())
                .retrieve()
                .bodyToMono(PRODUCTS_TYPE)
                .map(ApiResponse::getData)
                .flatMap(productImageUrlResolver::resolveMarketplacePage);

        Mono<PageResponse<MarketplaceProductResponse>> featuredProducts = productClient.get()
                .uri(uri -> uri.path("/products")
                        .queryParam("page", 0)
                        .queryParam("size", 8)
                        .queryParam("featuredOnly", true)
                        .queryParamIfPresent("categoryId", Optional.ofNullable(categoryId))
                        .queryParamIfPresent("search", Optional.ofNullable(search))
                        .build())
                .retrieve()
                .bodyToMono(PRODUCTS_TYPE)
                .map(ApiResponse::getData)
                .flatMap(productImageUrlResolver::resolveMarketplacePage);

        Mono<PageResponse<MarketplaceCategoryResponse>> categories = productClient.get()
                .uri(uri -> uri.path("/categories")
                        .queryParam("page", 0)
                        .queryParam("size", 100)
                        .build())
                .retrieve()
                .bodyToMono(CATEGORIES_TYPE)
                .map(ApiResponse::getData);

        Mono<UserContext> user = currentUser().cache();
        Mono<MarketplaceSummaryResponse> summary = user.flatMap(this::loadSummary);
        Mono<PageResponse<MarketplaceStoreResponse>> directory = storeClient.get()
                .uri(uri -> uri.path("/public/directory")
                        .queryParam("page", storePage)
                        .queryParam("size", storeSize)
                        .queryParamIfPresent("search", Optional.ofNullable(search))
                        .build())
                .retrieve()
                .bodyToMono(STORE_PAGE_TYPE)
                .map(ApiResponse::getData)
                .flatMap(this::enrichStorePage);
        return Mono.zip(products, featuredProducts, categories, user, summary, directory)
                .flatMap(tuple -> loadStores(java.util.stream.Stream.concat(
                                tuple.getT1().content().stream(), tuple.getT2().content().stream())
                        .map(MarketplaceProductResponse::storeId)
                        .filter(id -> id != null)
                        .distinct()
                        .toList())
                        .map(stores -> toResponse(tuple.getT1(), tuple.getT2(), tuple.getT3(), tuple.getT4(),
                                tuple.getT5(), stores, tuple.getT6())))
                .map(home -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), home)));
    }

    @GetMapping("/stores/{storeId}/categories")
    public Mono<ResponseEntity<ApiResponse<PageResponse<MarketplaceCategoryResponse>>>> getStoreCategories(
            @PathVariable @Positive Long storeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return publicStore(storeId).then(productClient.get()
                .uri(uri -> uri.path("/stores/{storeId}/categories")
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build(storeId))
                .retrieve()
                .bodyToMono(CATEGORIES_TYPE)
                .map(ResponseEntity::ok));
    }

    @GetMapping("/cart")
    public Mono<ResponseEntity<ApiResponse<MarketplaceHomeResponse.CartSummary>>> getCart() {
        return currentCustomerId().flatMap(userId -> loadSummaryStrict(userId)
                .map(summary -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), toCartSummary(summary)))));
    }

    @PostMapping("/cart/items")
    public Mono<ResponseEntity<ApiResponse<MarketplaceHomeResponse.CartSummary>>> addCartItem(
            @jakarta.validation.Valid @RequestBody MarketplaceCartItemRequest request) {
        return currentCustomerId().flatMap(userId -> loadSummaryStrict(userId).flatMap(summary -> {
                    com.x.bff.dto.MarketplaceCartItemResponse existing = summary.cartItems().stream()
                            .filter(item -> item.variantId().equals(request.variantId()))
                            .findFirst().orElse(null);
                    if (existing != null && !existing.storeId().equals(request.storeId())) {
                        return Mono.error(new org.springframework.web.server.ResponseStatusException(
                                HttpStatus.CONFLICT, "A variant cannot be in the cart for two stores"));
                    }
                    int previousQuantity = existing == null || existing.quantity() == null ? 0 : existing.quantity();
                    int quantity = previousQuantity + request.quantity();
                    if (quantity > 999) {
                        return Mono.error(new org.springframework.web.server.ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Cart quantity cannot exceed 999"));
                    }
                    return upsertCartItem(userId, new MarketplaceCartItemRequest(
                            request.productId(), request.storeId(), request.variantId(), quantity));
                })
                .map(summary -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), toCartSummary(summary)))));
    }

    @PutMapping("/cart/items/{variantId}")
    public Mono<ResponseEntity<ApiResponse<MarketplaceHomeResponse.CartSummary>>> updateCartItem(
            @PathVariable @Positive Long variantId,
            @jakarta.validation.Valid @RequestBody MarketplaceQuantityRequest request) {
        return currentCustomerId().flatMap(userId -> loadSummaryStrict(userId)
                .flatMap(summary -> summary.cartItems().stream()
                        .filter(item -> item.variantId().equals(variantId))
                        .findFirst()
                        .<Mono<MarketplaceSummaryResponse>>map(item -> upsertCartItem(userId,
                                new MarketplaceCartItemRequest(item.productId(), item.storeId(), item.variantId(), request.quantity())))
                        .orElseGet(() -> Mono.error(new org.springframework.web.server.ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Cart item not found"))))
                .map(summary -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), toCartSummary(summary)))));
    }

    @DeleteMapping("/cart/items/{variantId}")
    public Mono<ResponseEntity<ApiResponse<MarketplaceHomeResponse.CartSummary>>> removeCartItem(
            @PathVariable @Positive Long variantId) {
        return currentCustomerId().flatMap(userId -> customerClient.delete()
                        .uri(uri -> uri.path("/cart/items/{variantId}").queryParam("userId", userId).build(variantId))
                        .retrieve().toBodilessEntity()
                        .then(loadSummaryStrict(userId)))
                .map(summary -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), toCartSummary(summary))));
    }

    @GetMapping("/favorites")
    public Mono<ResponseEntity<ApiResponse<List<MarketplaceFavoriteResponse>>>> getFavorites() {
        return currentCustomerId().flatMap(userId -> customerClient.get()
                        .uri(uri -> uri.path("/favorites").queryParam("userId", userId).build())
                        .retrieve().bodyToMono(FAVORITES_TYPE).map(ApiResponse::getData))
                .map(favorites -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), favorites)));
    }

    @PostMapping("/favorites")
    public Mono<ResponseEntity<ApiResponse<List<MarketplaceFavoriteResponse>>>> addFavorite(
            @jakarta.validation.Valid @RequestBody MarketplaceFavoriteRequest request) {
        return currentCustomerId().flatMap(userId -> fetchProductDetail(request.productId(), request.storeId())
                        .flatMap(product -> publicStore(product.storeId()).then(customerClient.post()
                                .uri(uri -> uri.path("/favorites")
                                        .queryParam("userId", userId).queryParam("productId", product.productId())
                                        .queryParam("storeId", product.storeId()).build())
                                .retrieve().toBodilessEntity()
                                .then(loadFavorites(userId)))))
                .map(favorites -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), favorites)));
    }

    @DeleteMapping("/favorites/{productId}")
    public Mono<ResponseEntity<Void>> removeFavorite(@PathVariable @Positive Long productId) {
        return currentCustomerId().flatMap(userId -> customerClient.delete()
                        .uri(uri -> uri.path("/favorites/{productId}").queryParam("userId", userId).build(productId))
                        .retrieve().toBodilessEntity())
                .map(ignored -> ResponseEntity.noContent().<Void>build());
    }

    @PostMapping("/checkout/delivery-quotes")
    public Mono<ResponseEntity<ApiResponse<List<MarketplaceDeliveryQuoteResponse>>>> createDeliveryQuotes(
            @jakarta.validation.Valid @RequestBody MarketplaceDeliveryQuoteRequest request) {
        return currentCustomerId().then(deliveryQuotes(request));
    }

    @PostMapping("/guest/checkout/delivery-quotes")
    public Mono<ResponseEntity<ApiResponse<List<MarketplaceDeliveryQuoteResponse>>>> createGuestDeliveryQuotes(
            @jakarta.validation.Valid @RequestBody MarketplaceDeliveryQuoteRequest request) {
        return deliveryQuotes(request);
    }

    private Mono<ResponseEntity<ApiResponse<List<MarketplaceDeliveryQuoteResponse>>>> deliveryQuotes(
            MarketplaceDeliveryQuoteRequest request) {
        return publicStore(request.storeId()).then(deliveryClient.post().uri("/quotes")
                        .bodyValue(request).retrieve().bodyToMono(QUOTES_TYPE))
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @PostMapping("/checkout/orders")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> checkout(
            @jakarta.validation.Valid @RequestBody MarketplaceCheckoutRequest request) {
        return currentCustomerId().flatMap(userId -> createCheckout(userId, request));
    }

    @GetMapping("/orders")
    public Mono<ResponseEntity<ApiResponse<PageResponse<JsonNode>>>> getCustomerOrders(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return currentCustomerId().flatMap(userId -> orderClient.get()
                        .uri(uri -> uri.path("/customer/{customerId}").queryParam("page", page).queryParam("size", size).build(userId))
                        .retrieve().bodyToMono(ORDER_PAGE_TYPE))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/orders/{orderId}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getCustomerOrder(@PathVariable @Positive Long orderId) {
        return currentCustomerId().flatMap(userId -> orderClient.get()
                        .uri("/customer/{customerId}/{orderId}", userId, orderId)
                        .retrieve().bodyToMono(ORDER_TYPE))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/orders/{orderId}/payments")
    public Mono<ResponseEntity<ApiResponse<List<JsonNode>>>> getOrderPayments(@PathVariable @Positive Long orderId) {
        return currentCustomerId().flatMap(userId -> verifyCustomerOrder(userId, orderId)
                        .then(paymentClient.get().uri(uri -> uri.queryParam("orderId", orderId).build())
                                .retrieve().bodyToMono(PAYMENT_LIST_TYPE)))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/orders/{orderId}/payment/qr")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createOrderQrPayment(@PathVariable @Positive Long orderId) {
        return currentCustomerId().flatMap(userId -> verifyCustomerOrder(userId, orderId)
                        .flatMap(order -> createQrPayment(order, orderId, "marketplace-qr-" + userId + "-" + orderId)))
                .map(ResponseEntity::ok);
    }

    // ---- Guest checkout: no account; the cart lives in the browser and a signed token unlocks the order ----

    @PostMapping("/guest/checkout/orders")
    public Mono<ResponseEntity<ApiResponse<MarketplaceGuestOrderResponse>>> guestCheckout(
            @jakarta.validation.Valid @RequestBody MarketplaceGuestCheckoutRequest request) {
        if (!"CUSTOMER_PICKUP".equals(request.fulfillmentType())
                && (request.deliveryQuoteId() == null || request.deliveryAddress() == null)) {
            return Mono.error(status(HttpStatus.BAD_REQUEST, "Delivery quote and address are required for delivery"));
        }
        Map<Long, MarketplaceGuestCheckoutRequest.Item> lines = new LinkedHashMap<>();
        for (MarketplaceGuestCheckoutRequest.Item item : request.items()) {
            MarketplaceGuestCheckoutRequest.Item merged = lines.merge(item.variantId(), item, (left, right) ->
                    new MarketplaceGuestCheckoutRequest.Item(left.productId(), left.variantId(), left.quantity() + right.quantity()));
            if (merged.quantity() > 999) {
                return Mono.error(status(HttpStatus.BAD_REQUEST, "Quantity cannot exceed 999"));
            }
        }
        List<Long> productIds = lines.values().stream()
                .map(MarketplaceGuestCheckoutRequest.Item::productId).distinct().toList();
        return publicStore(request.storeId()).flatMap(store -> {
            if (store.businessId() == null) {
                return Mono.error(status(HttpStatus.CONFLICT, "Store is missing its business account"));
            }
            return Flux.fromIterable(productIds)
                    .flatMap(productId -> fetchProductDetail(productId, request.storeId()), 8)
                    .collectMap(MarketplaceProductDetailResponse::productId)
                    .flatMap(products -> placeGuestOrder(request, store, lines, products));
        });
    }

    @GetMapping("/guest/orders/{orderId}")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> getGuestOrder(
            @PathVariable @Positive Long orderId,
            @RequestHeader(value = GUEST_TOKEN_HEADER, required = false) String token) {
        return verifyGuestOrder(orderId, token)
                .map(order -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), order)));
    }

    @GetMapping("/guest/orders/{orderId}/payments")
    public Mono<ResponseEntity<ApiResponse<List<JsonNode>>>> getGuestOrderPayments(
            @PathVariable @Positive Long orderId,
            @RequestHeader(value = GUEST_TOKEN_HEADER, required = false) String token) {
        return verifyGuestOrder(orderId, token)
                .then(paymentClient.get().uri(uri -> uri.queryParam("orderId", orderId).build())
                        .retrieve().bodyToMono(PAYMENT_LIST_TYPE))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/guest/orders/{orderId}/payment/qr")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> createGuestOrderQrPayment(
            @PathVariable @Positive Long orderId,
            @RequestHeader(value = GUEST_TOKEN_HEADER, required = false) String token) {
        return verifyGuestOrder(orderId, token)
                .flatMap(order -> createQrPayment(order, orderId, "marketplace-qr-guest-" + orderId))
                .map(ResponseEntity::ok);
    }

    private Mono<ResponseEntity<ApiResponse<MarketplaceGuestOrderResponse>>> placeGuestOrder(
            MarketplaceGuestCheckoutRequest request,
            MarketplaceStoreResponse store,
            Map<Long, MarketplaceGuestCheckoutRequest.Item> lines,
            Map<Long, MarketplaceProductDetailResponse> products) {
        Set<String> currencies = new HashSet<>();
        List<OnlineOrderItemPayload> items = new ArrayList<>();
        for (MarketplaceGuestCheckoutRequest.Item line : lines.values()) {
            MarketplaceProductDetailResponse product = products.get(line.productId());
            if (product == null || !request.storeId().equals(product.storeId())) {
                return Mono.error(status(HttpStatus.BAD_REQUEST, "A product in the cart is not sold by this store"));
            }
            MarketplaceProductDetailResponse.Variant variant = product.variants() == null ? null : product.variants().stream()
                    .filter(candidate -> candidate.variantId().equals(line.variantId())).findFirst().orElse(null);
            if (variant == null) {
                return Mono.error(status(HttpStatus.BAD_REQUEST, product.productName() + " is no longer available in that option"));
            }
            if (!variant.inStock() || (variant.quantity() != null && line.quantity() > variant.quantity())) {
                return Mono.error(status(HttpStatus.CONFLICT, product.productName() + " does not have enough stock"));
            }
            if (product.currencyCode() != null) currencies.add(product.currencyCode().toUpperCase(Locale.ROOT));
            items.add(new OnlineOrderItemPayload(line.variantId(), line.quantity()));
        }
        if (currencies.size() != 1) {
            return Mono.error(status(HttpStatus.CONFLICT, "An order can contain products in one currency only"));
        }
        String currency = currencies.iterator().next();
        if ("QR".equals(request.paymentType()) && !"USD".equals(currency) && !"KHR".equals(currency)) {
            return Mono.error(status(HttpStatus.BAD_REQUEST, "KHQR supports only USD and KHR orders"));
        }
        // The client's key is bound to the store and phone, so replaying someone else's key cannot return their order.
        String key = "marketplace-guest-" + sha256Hex(request.storeId() + "|"
                + request.contactPhone().replaceAll("[^0-9+]", "") + "|" + request.idempotencyKey()).substring(0, 40);
        OnlineOrderPayload payload = new OnlineOrderPayload(
                store.businessId(), request.storeId(), null, request.contactName().trim(), request.contactPhone().trim(),
                currency, request.fulfillmentType(), request.paymentType(), request.deliveryQuoteId(),
                request.deliveryAddress(), request.discountCode(), null, request.note(), key, items);
        // A guest has no session to pass on, but the order service reads prices from the product service, which
        // needs a signed caller. A short service token for the marketplace stands in for the guest.
        return orderClient.post().uri("/online")
                .headers(headers -> headers.setBearerAuth(jwtUtils.generateToken(GUEST_SERVICE_USER, Set.of())))
                .bodyValue(payload).retrieve().bodyToMono(ORDER_TYPE)
                .map(ApiResponse::getData)
                .map(order -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                        HttpStatus.CREATED.value(),
                        new MarketplaceGuestOrderResponse(order, guestOrderTokens.issue(order.path("id").asLong())))));
    }

    /** A guest order is readable only with its token, and only while no customer account is attached to it. */
    private Mono<JsonNode> verifyGuestOrder(Long orderId, String token) {
        if (!guestOrderTokens.verify(orderId, token)) {
            return Mono.error(status(HttpStatus.NOT_FOUND, "Order not found"));
        }
        return orderClient.get().uri("/{id}", orderId)
                .retrieve().bodyToMono(ORDER_TYPE)
                .map(ApiResponse::getData)
                .filter(order -> (order.path("customerId").isNull() || order.path("customerId").isMissingNode())
                        && "ONLINE".equals(order.path("orderChannel").asText()))
                .switchIfEmpty(Mono.error(status(HttpStatus.NOT_FOUND, "Order not found")));
    }

    private Mono<ApiResponse<JsonNode>> createQrPayment(JsonNode order, Long orderId, String idempotencyKey) {
        if (!"UNPAID".equals(order.path("paymentStatus").asText())) {
            return Mono.error(status(HttpStatus.CONFLICT, "This order is not awaiting QR payment"));
        }
        String currency = order.path("currencyCode").asText("");
        if (!"USD".equalsIgnoreCase(currency) && !"KHR".equalsIgnoreCase(currency)) {
            return Mono.error(status(HttpStatus.BAD_REQUEST, "KHQR supports only USD and KHR orders"));
        }
        long businessId = order.path("businessId").asLong();
        // Bakong KHQR, as on the POS: paid straight into the merchant's own Bakong account, in USD or riel.
        return businessClient.get().uri("/{id}", businessId).retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<ApiResponse<com.x.bff.dto.BusinessResponse>>() {})
                .mapNotNull(ApiResponse::getData)
                .switchIfEmpty(Mono.error(status(HttpStatus.CONFLICT, "The store's business account was not found")))
                .flatMap(business -> {
                    String account = business.bakongAccountId();
                    if (account == null || account.isBlank()) {
                        return Mono.error(status(HttpStatus.CONFLICT,
                                "This store does not take KHQR yet. Please choose cash instead."));
                    }
                    String orderNo = order.path("orderNo").asText("");
                    BakongQrPaymentPayload payload = new BakongQrPaymentPayload(
                            orderId, businessId, order.path("storeId").asLong(),
                            order.path("grandTotal").decimalValue(), currency, idempotencyKey,
                            "Marketplace order " + orderNo, null, account.trim(), business.name(),
                            orderNo.isBlank() ? null : orderNo.substring(0, Math.min(25, orderNo.length())));
                    return paymentClient.post().uri("/bakong-qr").bodyValue(payload).retrieve().bodyToMono(ORDER_TYPE);
                });
    }

    private static ResponseStatusException status(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
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

    private Mono<Long> currentCustomerId() {
        return currentUser().flatMap(user -> user.id() == null
                ? Mono.error(new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "A customer account is required"))
                : Mono.just(user.id()));
    }

    private Mono<MarketplaceSummaryResponse> loadSummaryStrict(Long userId) {
        return customerClient.get()
                .uri(uri -> uri.path("/summary").queryParam("userId", userId).build())
                .retrieve().bodyToMono(SUMMARY_TYPE)
                .map(ApiResponse::getData)
                .switchIfEmpty(Mono.just(emptySummary()));
    }

    private Mono<JsonNode> verifyCustomerOrder(Long userId, Long orderId) {
        return orderClient.get()
                .uri("/customer/{customerId}/{orderId}", userId, orderId)
                .retrieve().bodyToMono(ORDER_TYPE)
                .map(ApiResponse::getData)
                .filter(order -> order.path("customerId").asLong(-1) == userId
                        && "ONLINE".equals(order.path("orderChannel").asText()))
                .switchIfEmpty(Mono.error(new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order not found")));
    }

    private Mono<List<MarketplaceFavoriteResponse>> loadFavorites(Long userId) {
        return customerClient.get()
                .uri(uri -> uri.path("/favorites").queryParam("userId", userId).build())
                .retrieve().bodyToMono(FAVORITES_TYPE)
                .map(ApiResponse::getData)
                .defaultIfEmpty(List.of());
    }

    private Mono<MarketplaceSummaryResponse> upsertCartItem(Long userId, MarketplaceCartItemRequest request) {
        return fetchProductDetail(request.productId(), request.storeId())
                .flatMap(product -> publicStore(product.storeId()).then(Mono.defer(() -> {
                    MarketplaceProductDetailResponse.Variant variant = product.variants().stream()
                            .filter(candidate -> candidate.variantId().equals(request.variantId()))
                            .findFirst()
                            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                                    HttpStatus.BAD_REQUEST, "Variant is not available for this product"));
                    if (!variant.inStock() || (variant.quantity() != null && request.quantity() > variant.quantity())) {
                        return Mono.error(new org.springframework.web.server.ResponseStatusException(
                                HttpStatus.CONFLICT, "Requested quantity is not currently available"));
                    }
                    return loadSummaryStrict(userId).flatMap(summary -> {
                        boolean mixedCurrency = summary.cartItems().stream()
                                .map(com.x.bff.dto.MarketplaceCartItemResponse::currencyCode)
                                .filter(java.util.Objects::nonNull)
                                .anyMatch(currency -> !currency.equalsIgnoreCase(product.currencyCode()));
                        if (mixedCurrency) {
                            return Mono.error(new org.springframework.web.server.ResponseStatusException(
                                    HttpStatus.CONFLICT, "A cart can contain products in one currency only"));
                        }
                        CustomerCartItemPayload payload = new CustomerCartItemPayload(
                                product.productId(), product.storeId(), variant.variantId(), product.productName(),
                                product.thumbnail(), variant.onlinePrice(), product.currencyCode(), request.quantity());
                        return customerClient.put()
                                .uri(uri -> uri.path("/cart/items").queryParam("userId", userId).build())
                                .bodyValue(payload).retrieve().toBodilessEntity()
                                .then(loadSummaryStrict(userId));
                    });
                })));
    }

    private Mono<MarketplaceProductDetailResponse> fetchProductDetail(Long productId, Long storeId) {
        return productClient.get()
                .uri(uri -> uri.path("/products/{productId}")
                        .queryParamIfPresent("storeId", Optional.ofNullable(storeId))
                        .build(productId))
                .retrieve().bodyToMono(PRODUCT_DETAIL_TYPE)
                .map(ApiResponse::getData)
                .flatMap(productImageUrlResolver::resolveMarketplaceDetail);
    }

    private Mono<MarketplaceStoreResponse> publicStore(Long storeId) {
        return storeClient.get()
                .uri("/public/{id}", storeId)
                .retrieve().bodyToMono(STORE_TYPE)
                .map(ApiResponse::getData)
                .flatMap(store -> withoutSuspendedMerchants(List.of(store)))
                .flatMap(stores -> stores.isEmpty()
                        ? Mono.error(new org.springframework.web.server.ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Marketplace store not found"))
                        : storeImageUrlResolver.resolveMarketplaceStores(stores).map(items -> items.get(0)));
    }

    private Mono<PageResponse<MarketplaceProductView>> enrichProductPage(
            PageResponse<MarketplaceProductResponse> page) {
        return productImageUrlResolver.resolveMarketplacePage(page)
                .flatMap(resolved -> loadStores(resolved.content().stream()
                                .map(MarketplaceProductResponse::storeId).filter(java.util.Objects::nonNull).distinct().toList())
                        .map(stores -> toProductViews(resolved, stores.stream().collect(Collectors.toMap(
                                MarketplaceStoreResponse::id, Function.identity(), (left, right) -> left)))));
    }

    private Mono<PageResponse<MarketplaceStoreResponse>> enrichStorePage(
            PageResponse<MarketplaceStoreResponse> page) {
        return withoutSuspendedMerchants(page.content())
                .flatMap(storeImageUrlResolver::resolveMarketplaceStores)
                .map(visible -> new PageResponse<>(visible, page.page(), page.size(), visibleTotal(page, visible.size()),
                        page.totalPages(), page.hasNext()));
    }

    /**
     * The store and product services count every approved store and product, but stores of suspended merchants
     * and merchants without the marketplace plan are hidden here. The total leaves out what this page hid, so a
     * one-page result (the usual case) reports exactly what the shopper sees.
     */
    private static long visibleTotal(PageResponse<?> page, int shown) {
        int hidden = page.content().size() - shown;
        return Math.max(shown, page.totalElements() - hidden);
    }

    private MarketplaceHomeResponse.CartSummary toCartSummary(MarketplaceSummaryResponse summary) {
        return new MarketplaceHomeResponse.CartSummary(
                summary.cartItemCount(), summary.cartSubtotal(), summary.cartCurrency(), summary.cartItems());
    }

    private Mono<ResponseEntity<ApiResponse<JsonNode>>> createCheckout(
            Long userId, MarketplaceCheckoutRequest request) {
        if (!"CUSTOMER_PICKUP".equals(request.fulfillmentType())
                && (request.deliveryQuoteId() == null || request.deliveryAddress() == null)) {
            return Mono.error(new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Delivery quote and address are required for delivery"));
        }
        return loadSummaryStrict(userId).flatMap(summary -> {
            List<com.x.bff.dto.MarketplaceCartItemResponse> cartItems = summary.cartItems() == null ? List.of()
                    : summary.cartItems().stream().filter(item -> request.storeId().equals(item.storeId())).toList();
            if (cartItems.isEmpty()) {
                return Mono.error(new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.CONFLICT, "The cart has no items for this store"));
            }
            Set<String> currencies = cartItems.stream().map(com.x.bff.dto.MarketplaceCartItemResponse::currencyCode)
                    .filter(java.util.Objects::nonNull).map(code -> code.toUpperCase(java.util.Locale.ROOT))
                    .collect(Collectors.toSet());
            if (currencies.size() != 1) {
                return Mono.error(new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.CONFLICT, "Cart items for a store must use one currency"));
            }
            String orderCurrency = currencies.iterator().next();
            if ("QR".equals(request.paymentType()) && !"USD".equals(orderCurrency) && !"KHR".equals(orderCurrency)) {
                return Mono.error(new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "KHQR supports only USD and KHR orders"));
            }
            return publicStore(request.storeId()).flatMap(store -> {
                if (store.businessId() == null) {
                    return Mono.error(new org.springframework.web.server.ResponseStatusException(
                            HttpStatus.CONFLICT, "Store is missing its business account"));
                }
                List<OnlineOrderItemPayload> items = cartItems.stream()
                        .map(item -> new OnlineOrderItemPayload(item.variantId(), item.quantity())).toList();
                String key = "marketplace-" + userId + "-" + request.idempotencyKey();
                OnlineOrderPayload payload = new OnlineOrderPayload(
                        store.businessId(), request.storeId(), userId, trimToNull(request.contactName()),
                        trimToNull(request.contactPhone()), currencies.iterator().next(),
                        request.fulfillmentType(), request.paymentType(), request.deliveryQuoteId(),
                        request.deliveryAddress(), request.discountCode(), null, request.note(), key, items);
                return orderClient.post().uri("/online").bodyValue(payload).retrieve().bodyToMono(ORDER_TYPE)
                        .flatMap(order -> customerClient.delete()
                                .uri(uri -> uri.path("/cart/stores/{storeId}").queryParam("userId", userId).build(request.storeId()))
                                .retrieve().toBodilessEntity().onErrorResume(error -> Mono.empty())
                                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(order)));
            });
        });
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
                .flatMap(this::withoutSuspendedMerchants)
                .flatMap(storeImageUrlResolver::resolveMarketplaceStores)
                .defaultIfEmpty(List.of());
    }

    /** Shops of a suspended merchant, or of a merchant without the marketplace in its plan, are not shown. */
    private Mono<List<MarketplaceStoreResponse>> withoutSuspendedMerchants(List<MarketplaceStoreResponse> stores) {
        List<Long> businessIds = stores.stream().map(MarketplaceStoreResponse::businessId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (businessIds.isEmpty()) return Mono.just(stores);
        return reactor.core.publisher.Flux.fromIterable(businessIds)
                .flatMap(id -> businessClient.get().uri("/{id}", id).retrieve()
                        .bodyToMono(new org.springframework.core.ParameterizedTypeReference<ApiResponse<com.x.bff.dto.BusinessResponse>>() {})
                        .mapNotNull(ApiResponse::getData)
                        // A merchant that cannot be looked up is kept, so a business-service hiccup does not empty the marketplace.
                        .onErrorResume(error -> Mono.empty()))
                // Hidden: suspended merchants, and merchants whose plan does not include the marketplace (Pro only).
                .filter(business -> business.suspended()
                        || (business.plan() != null && !"PRO".equalsIgnoreCase(business.plan())))
                .map(com.x.bff.dto.BusinessResponse::id)
                .collect(java.util.stream.Collectors.toSet())
                .map(suspended -> stores.stream()
                        .filter(store -> store.businessId() == null || !suspended.contains(store.businessId()))
                        .toList());
    }

    private MarketplaceHomeResponse toResponse(
            PageResponse<MarketplaceProductResponse> products,
            PageResponse<MarketplaceProductResponse> featuredProducts,
            PageResponse<MarketplaceCategoryResponse> categories,
            UserContext user,
            MarketplaceSummaryResponse summary,
            List<MarketplaceStoreResponse> stores,
            PageResponse<MarketplaceStoreResponse> directory) {
        Map<Long, MarketplaceStoreResponse> storesById = stores.stream()
                .collect(Collectors.toMap(MarketplaceStoreResponse::id, Function.identity(), (left, right) -> left));
        PageResponse<MarketplaceProductView> productViews = toProductViews(products, storesById);
        List<MarketplaceProductView> featuredProductViews = toProductViews(featuredProducts, storesById).content();
        return new MarketplaceHomeResponse(
                productViews,
                featuredProductViews,
                categories.content(),
                directory,
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

    private PageResponse<MarketplaceProductView> toProductViews(
            PageResponse<MarketplaceProductResponse> products,
            Map<Long, MarketplaceStoreResponse> storesById) {
        List<MarketplaceProductView> views = products.content().stream()
                .filter(product -> storesById.containsKey(product.storeId()))
                .map(product -> new MarketplaceProductView(
                        product.productId(), product.productName(), product.shortName(), product.thumbnail(),
                        product.description(), product.categoryId(), product.categoryName(), product.currencyCode(),
                        product.onlinePrice(), product.compareAtPrice(), product.quantity(), product.featured(),
                        storesById.get(product.storeId())))
                .toList();
        return new PageResponse<>(views, products.page(), products.size(), visibleTotal(products, views.size()),
                products.totalPages(), products.hasNext());
    }

    private MarketplaceSummaryResponse emptySummary() {
        return new MarketplaceSummaryResponse(null, List.of(), 0, 0, BigDecimal.ZERO, null, List.of());
    }

    private record UserContext(Long id, String username, String fullName, String email, boolean authenticated) {
    }

    private record CustomerCartItemPayload(
            Long productId, Long storeId, Long variantId, String productName, String thumbnail,
            BigDecimal unitPrice, String currencyCode, Integer quantity) {
    }

    private record OnlineOrderItemPayload(Long variantId, Integer quantity) {
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record OnlineOrderPayload(
            Long businessId,
            Long storeId,
            Long customerId,
            String contactName,
            String contactPhone,
            String currencyCode,
            String fulfillmentType,
            String paymentType,
            Long deliveryQuoteId,
            MarketplaceCheckoutRequest.DeliveryAddress deliveryAddress,
            String discountCode,
            BigDecimal taxRate,
            String note,
            String idempotencyKey,
            List<OnlineOrderItemPayload> items) {
    }

    private record BakongQrPaymentPayload(
            Long orderId,
            Long businessId,
            Long storeId,
            BigDecimal amount,
            String currencyCode,
            String idempotencyKey,
            String note,
            Long cashierId,
            String bakongAccountId,
            String merchantName,
            String billNumber) {
    }
}
