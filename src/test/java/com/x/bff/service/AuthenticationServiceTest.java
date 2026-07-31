package com.x.bff.service;

import com.x.bff.dto.AuthRequest;
import com.x.bff.security.ClientChannel;
import com.x.bff.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticationServiceTest {

    private JwtUtils jwtUtils;
    private UserServiceClient userServiceClient;
    private ServiceClientFactory serviceClientFactory;
    private StoreImageUrlResolver storeImageUrlResolver;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        jwtUtils = mock(JwtUtils.class);
        userServiceClient = mock(UserServiceClient.class);
        serviceClientFactory = mock(ServiceClientFactory.class);
        storeImageUrlResolver = mock(StoreImageUrlResolver.class);
        when(storeImageUrlResolver.resolvePage(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(serviceClientFactory.forService("business", "/api/v1/businesses"))
                .thenReturn(businessClient());
        when(serviceClientFactory.forService("store", "/api/v1/stores"))
                .thenReturn(storeClient());
    }

    @Test
    void authenticateReturnsTokensForValidBcryptCredentials() {
        String hashed = passwordEncoder.encode("pass");
        when(userServiceClient.findByUsername("user")).thenReturn(Mono.just(
                new com.x.bff.dto.UserCredentialsResponse(
                        1L, "user", hashed, Set.of("x-product:read"))));
        when(jwtUtils.generateToken(eq("user"), eq(Set.of("x-product:read")), eq("mobile"), eq(1L)))
                .thenReturn("access");
        when(jwtUtils.generateRefreshToken(eq("user"), eq("mobile"))).thenReturn("refresh");
        when(jwtUtils.getAccessExpirationSeconds()).thenReturn(900L);

        AuthenticationService service = new AuthenticationService(jwtUtils, userServiceClient, serviceClientFactory, storeImageUrlResolver);

        StepVerifier.create(service.authenticate(new AuthRequest("user", "pass"), ClientChannel.MOBILE))
                .expectNextMatches(response -> response.getCode() == 200
                        && "access".equals(response.getData().getAccessToken())
                        && "refresh".equals(response.getData().getRefreshToken())
                        && "mobile".equals(response.getData().getChannel())
                        && Long.valueOf(900L).equals(response.getData().getExpiresIn())
                        && "user".equals(response.getData().getUser().getUsername())
                        && Long.valueOf(12L).equals(response.getData().getBusiness().id())
                        && response.getData().getStores().size() == 1)
                .verifyComplete();
    }

    @Test
    void authenticateRejectsPlaintextStoredPassword() {
        when(userServiceClient.findByUsername("user")).thenReturn(Mono.just(
                new com.x.bff.dto.UserCredentialsResponse(
                        1L, "user", "plaintext-pass", Set.of())));

        AuthenticationService service = new AuthenticationService(jwtUtils, userServiceClient, serviceClientFactory, storeImageUrlResolver);

        StepVerifier.create(service.authenticate(new AuthRequest("user", "plaintext-pass"), ClientChannel.MOBILE))
                .expectError(BadCredentialsException.class)
                .verify();
    }

    @Test
    void authenticateRejectsInvalidCredentials() {
        String hashed = passwordEncoder.encode("expected");
        when(userServiceClient.findByUsername("user")).thenReturn(Mono.just(
                new com.x.bff.dto.UserCredentialsResponse(
                        1L, "user", hashed, Set.of())));

        AuthenticationService service = new AuthenticationService(jwtUtils, userServiceClient, serviceClientFactory, storeImageUrlResolver);

        StepVerifier.create(service.authenticate(new AuthRequest("user", "wrong"), ClientChannel.WEB))
                .expectError(BadCredentialsException.class)
                .verify();
    }

    @Test
    void refreshRejectsBlankToken() {
        AuthenticationService service = new AuthenticationService(jwtUtils, userServiceClient, serviceClientFactory, storeImageUrlResolver);

        StepVerifier.create(service.refresh("  ", ClientChannel.MOBILE))
                .expectError(BadCredentialsException.class)
                .verify();
    }

    @Test
    void refreshIssuesNewTokensForValidRefreshJwt() {
        String hashed = passwordEncoder.encode("pass");
        when(jwtUtils.extractUsername("refresh-jwt")).thenReturn("user");
        when(jwtUtils.validateRefreshToken("refresh-jwt", "user")).thenReturn(true);
        when(userServiceClient.findByUsername("user")).thenReturn(Mono.just(
                new com.x.bff.dto.UserCredentialsResponse(
                        1L, "user", hashed, Set.of("x-product:read"))));
        when(jwtUtils.generateToken(any(), any(), any(), anyLong())).thenReturn("access2");
        when(jwtUtils.generateRefreshToken(any(), any())).thenReturn("refresh2");
        when(jwtUtils.getAccessExpirationSeconds()).thenReturn(900L);

        AuthenticationService service = new AuthenticationService(jwtUtils, userServiceClient, serviceClientFactory, storeImageUrlResolver);

        StepVerifier.create(service.refresh("refresh-jwt", ClientChannel.WEB))
                .expectNextMatches(response -> "access2".equals(response.getData().getAccessToken())
                        && "web".equals(response.getData().getChannel()))
                .verifyComplete();
    }

    private static WebClient businessClient() {
        return WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .body("""
                                {"status":1,"code":200,"data":[{"id":12,"ownerUserId":1,"name":"Demo Business","code":"DEMO","defaultCurrencyCode":"USD","pricesIncludeTax":true,"timeZone":"Asia/Phnom_Penh","fiscalYearStartMonth":1,"status":1}]}
                                """)
                        .build()))
                .build();
    }

    private static WebClient storeClient() {
        return WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .body("""
                                {"status":1,"code":200,"data":{"content":[{"id":21,"businessId":12,"name":"Main Store","code":"MAIN","status":1}],"page":0,"size":100,"totalElements":1,"totalPages":1}}
                                """)
                        .build()))
                .build();
    }
}
