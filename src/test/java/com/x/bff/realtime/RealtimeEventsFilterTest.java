package com.x.bff.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RealtimeEventsFilterTest {

    private final RealtimePublisher realtime = mock(RealtimePublisher.class);
    private final RealtimeEventsFilter filter = new RealtimeEventsFilter(realtime);

    @Test
    void approvingAMerchantUpdatesTheBackofficeQueueAndTheMerchant() {
        run(MockServerHttpRequest.post("/api/v1/businesses/12/approval/approve").build(), HttpStatus.OK);

        verify(realtime).toAdmins("approvals.changed", Map.of());
        verify(realtime).toBusiness(12L, "business.updated", Map.of());
    }

    @Test
    void aNewSignUpOrMarketplaceApplicationUpdatesTheQueue() {
        run(MockServerHttpRequest.post("/api/v1/auth/register").build(), HttpStatus.CREATED);
        run(MockServerHttpRequest.post("/api/v1/stores/5/marketplace/apply").build(), HttpStatus.OK);

        verify(realtime, org.mockito.Mockito.times(2)).toAdmins("approvals.changed", Map.of());
        verify(realtime, never()).toBusiness(any(), anyString(), any());
    }

    @Test
    void suspendingTellsTheMerchantButFailuresAndReadsTellNobody() {
        run(MockServerHttpRequest.delete("/api/v1/admin/merchants/2/suspension").build(), HttpStatus.OK);
        verify(realtime).toBusiness(2L, "business.updated", Map.of());

        RealtimePublisher quiet = mock(RealtimePublisher.class);
        RealtimeEventsFilter quietFilter = new RealtimeEventsFilter(quiet);
        MockServerWebExchange refused = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/businesses/12/approval/approve").build());
        quietFilter.filter(refused, exchange -> {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return Mono.empty();
        }).block();
        quietFilter.filter(MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/businesses/approvals/pending").build()),
                exchange -> Mono.empty()).block();
        verifyNoInteractions(quiet);
    }

    private void run(MockServerHttpRequest request, HttpStatus status) {
        filter.filter(MockServerWebExchange.from(request), exchange -> {
            exchange.getResponse().setStatusCode(status);
            return Mono.empty();
        }).block();
    }
}
