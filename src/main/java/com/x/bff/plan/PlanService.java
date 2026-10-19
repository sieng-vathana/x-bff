package com.x.bff.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.sharedlib.response.ApiResponse;
import com.x.bff.service.ServiceClientFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the plan catalog and a merchant's plan from the business service, and counts what a merchant uses
 * (stores, staff, products) so the plan's limits can be checked.
 */
@Service
public class PlanService {
    private static final ParameterizedTypeReference<ApiResponse<JsonNode>> JSON = new ParameterizedTypeReference<>() {};
    /** Merchants created before plans have none and keep every feature. */
    public static final String LEGACY_PLAN = "PRO";

    private final WebClient businessClient;
    private final WebClient storeClient;
    private final WebClient productClient;
    private final WebClient userClient;
    private final Mono<Map<String, JsonNode>> catalog;

    public PlanService(ServiceClientFactory clientFactory, WebClient.Builder webClientBuilder,
                       @org.springframework.beans.factory.annotation.Value("${services.user.base-url:http://localhost:8081/internal/users}") String userBaseUrl) {
        this.businessClient = clientFactory.forService("business", "/api/v1/businesses");
        this.storeClient = clientFactory.forService("store", "/api/v1/stores");
        this.productClient = clientFactory.forService("product", "/api/v1/products");
        this.userClient = webClientBuilder.clone().baseUrl(userBaseUrl).build();
        this.catalog = businessClient.get().uri("/plans").retrieve().bodyToMono(JSON)
                .mapNotNull(ApiResponse::getData)
                .map(plans -> {
                    Map<String, JsonNode> byCode = new HashMap<>();
                    plans.forEach(plan -> byCode.put(plan.path("code").asText(), plan));
                    return byCode;
                })
                // Prices and features change rarely; a failed load is retried on the next request.
                .cache(plans -> Duration.ofMinutes(10), error -> Duration.ZERO, () -> Duration.ZERO);
    }

    /** The plan catalog as the business service serves it (for sign-up and the portal). */
    public Mono<JsonNode> plans() {
        return businessClient.get().uri("/plans").retrieve().bodyToMono(JSON).mapNotNull(ApiResponse::getData);
    }

public Mono<JsonNode> businessOf(Long businessId) {
        return businessClient.get().uri("/{id}", businessId).retrieve().bodyToMono(JSON).mapNotNull(ApiResponse::getData);
    }

    /** The merchant's plan entry from the catalog. */
    public Mono<JsonNode> planOf(Long businessId) {
        return businessClient.get().uri("/{id}", businessId).retrieve().bodyToMono(JSON)
                .mapNotNull(ApiResponse::getData)
                .map(business -> business.path("plan").asText(LEGACY_PLAN))
                .defaultIfEmpty(LEGACY_PLAN)
                .flatMap(code -> catalog.mapNotNull(plans -> plans.getOrDefault(code, plans.get(LEGACY_PLAN))));
    }

    public static boolean includes(JsonNode plan, String feature) {
        for (JsonNode included : plan.path("features")) {
            if (feature.equals(included.asText())) return true;
        }
        return false;
    }

    /** null when the plan has no limit of this kind. */
    public static Integer limit(JsonNode plan, String field) {
        JsonNode value = plan.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    public Mono<Long> countStores(Long businessId) {
        return storeIds(businessId).map(ids -> (long) ids.size());
    }

    public Mono<Long> countStaff(Long businessId) {
        return userClient.get()
                .uri(uri -> uri.queryParam("businessId", businessId).queryParam("page", 0).queryParam("size", 1).build())
                .retrieve().bodyToMono(JSON).mapNotNull(ApiResponse::getData)
                .map(page -> page.path("totalElements").asLong(0))
                .defaultIfEmpty(0L);
    }

    public Mono<Long> countProducts(Long businessId) {
        return storeIds(businessId).flatMap(ids -> ids.isEmpty() ? Mono.just(0L) : productClient.get()
                .uri(uri -> {
                    uri.path("/count");
                    ids.forEach(id -> uri.queryParam("storeIds", id));
                    return uri.build();
                })
                .retrieve().bodyToMono(JSON).mapNotNull(ApiResponse::getData)
                .map(JsonNode::asLong)
                .defaultIfEmpty(0L));
    }

    private Mono<List<Long>> storeIds(Long businessId) {
        return storeClient.get()
                .uri(uri -> uri.queryParam("businessId", businessId).queryParam("page", 0).queryParam("size", 100).build())
                .retrieve().bodyToMono(JSON).mapNotNull(ApiResponse::getData)
                .flatMapMany(page -> Flux.fromIterable(page.path("content")))
                .map(store -> store.path("id").asLong())
                .collectList();
    }
}
