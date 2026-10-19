package com.x.bff.plan;

import com.sharedlib.response.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** The plans a merchant can choose, with prices, limits and features. Public: the sign-up page shows them. */
@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {
    private final PlanService planService;
    private final com.x.bff.security.JwtUtils jwtUtils;

    public PlanController(PlanService planService, com.x.bff.security.JwtUtils jwtUtils) {
        this.planService = planService;
        this.jwtUtils = jwtUtils;
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> plans() {
        return planService.plans().map(plans -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), plans)));
    }

    /** The signed-in merchant's plan, trial and how much of each limit it uses. */
    @GetMapping("/mine")
    public Mono<ResponseEntity<ApiResponse<java.util.Map<String, Object>>>> mine(
            org.springframework.security.core.Authentication authentication) {
        Long businessId = authentication.getCredentials() instanceof String token ? jwtUtils.extractBusinessId(token) : null;
        if (businessId == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(HttpStatus.NOT_FOUND.value(), "Sign in again to see your plan.")));
        }
        return Mono.zip(planService.planOf(businessId), planService.businessOf(businessId),
                        planService.countStores(businessId), planService.countStaff(businessId), planService.countProducts(businessId))
                .map(tuple -> {
                    java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
                    body.put("plan", tuple.getT1());
                    body.put("billingCycle", tuple.getT2().path("billingCycle").asText(null));
                    body.put("trialEndsAt", tuple.getT2().path("trialEndsAt").asText(null));
                    body.put("usage", java.util.Map.of("stores", tuple.getT3(), "staff", tuple.getT4(), "products", tuple.getT5()));
                    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), body));
                });
    }
}
