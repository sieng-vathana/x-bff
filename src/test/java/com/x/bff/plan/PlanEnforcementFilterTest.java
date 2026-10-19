package com.x.bff.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.bff.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanEnforcementFilterTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private PlanService planService;
    private PlanEnforcementFilter filter;

    private static JsonNode plan(String code, String name, Integer maxStores, String... features) throws Exception {
        return MAPPER.readTree(MAPPER.writeValueAsString(java.util.Map.of(
                "code", code, "name", name, "maxStores", maxStores == null ? "unlimited" : maxStores,
                "features", List.of(features))));
    }

    @BeforeEach
    void setUp() {
        planService = mock(PlanService.class);
        JwtUtils jwtUtils = mock(JwtUtils.class);
        when(jwtUtils.extractBusinessId("starter-token")).thenReturn(2L);
        when(jwtUtils.extractBusinessId("business-token")).thenReturn(3L);
        when(jwtUtils.extractBusinessId("old-token")).thenReturn(null);
        filter = new PlanEnforcementFilter(planService, jwtUtils);
    }

    private record Result(boolean passed, MockServerWebExchange exchange) {
    }

    private Result run(String method, String path, String token) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(org.springframework.http.HttpMethod.valueOf(method), path).build());
        AtomicBoolean passed = new AtomicBoolean(false);
        WebFilterChain chain = ignored -> {
            passed.set(true);
            return Mono.empty();
        };
        StepVerifier.create(filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                new UsernamePasswordAuthenticationToken("owner", token, List.of()))))
                .verifyComplete();
        return new Result(passed.get(), exchange);
    }

    @Test
    void featureOutsideThePlanIsRefusedWithTheUpgradeHint() throws Exception {
        when(planService.planOf(2L)).thenReturn(Mono.just(plan("STARTER", "Starter", 1)));
        when(planService.planOf(3L)).thenReturn(Mono.just(plan("BUSINESS", "Business", 3, "PURCHASING", "INVENTORY")));

        Result starter = run("GET", "/api/v1/purchases/orders", "starter-token");
        assertFalse(starter.passed());
        assertEquals(HttpStatus.FORBIDDEN, starter.exchange().getResponse().getStatusCode());
        String body = starter.exchange().getResponse().getBodyAsString().block();
        assertTrue(body.contains("PLAN_REQUIRED"));
        assertTrue(body.contains("Purchasing is not in your Starter plan"));

        assertTrue(run("GET", "/api/v1/purchases/orders", "business-token").passed());
        assertFalse(run("POST", "/api/v1/stores/5/marketplace/apply", "business-token").passed());
    }

    @Test
    void creatingBeyondTheLimitIsRefused() throws Exception {
        when(planService.planOf(2L)).thenReturn(Mono.just(plan("STARTER", "Starter", 1)));
        when(planService.countStores(2L)).thenReturn(Mono.just(1L));

        Result result = run("POST", "/api/v1/stores", "starter-token");
        assertFalse(result.passed());
        String body = result.exchange().getResponse().getBodyAsString().block();
        assertTrue(body.contains("PLAN_LIMIT"));
        assertTrue(body.contains("allows 1 store and you have 1"));
    }

    @Test
    void ungatedPathsAndOldSessionsAreNotChecked() {
        assertTrue(run("GET", "/api/v1/products", "starter-token").passed());
        assertTrue(run("GET", "/api/v1/purchases/orders", "old-token").passed());
    }
}
