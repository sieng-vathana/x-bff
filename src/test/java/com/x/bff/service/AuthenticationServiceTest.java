package com.x.bff.service;

import com.x.bff.dto.AuthRequest;
import com.x.bff.dto.EmailMfaChallenge;
import com.x.bff.dto.EmailMfaStatus;
import com.x.bff.dto.TelegramMfaChallenge;
import com.x.bff.dto.TelegramMfaStatus;
import com.x.bff.dto.RegistrationRequest;
import com.x.bff.security.ClientChannel;
import com.x.bff.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        when(userServiceClient.ensureOwnerBusinessAccess(anyLong(), anyLong(), any()))
                .thenReturn(Mono.empty());
        givenMfa(false, false);
    }

    @Test
    void withTelegramAndEmailOnTheUserChoosesAndNoCodeIsSentYet() {
        givenUserWithPassword("pass");
        givenMfa(true, true);
        when(jwtUtils.generateMfaSelectionToken("user")).thenReturn("select-token");
        AuthenticationService service = new AuthenticationService(jwtUtils, userServiceClient, serviceClientFactory, storeImageUrlResolver);

        StepVerifier.create(service.authenticate(new AuthRequest("user", "pass"), ClientChannel.WEB))
                .expectNextMatches(response -> Boolean.TRUE.equals(response.getData().getMfaRequired())
                        && "CHOOSE".equals(response.getData().getMfaMethod())
                        && List.of("TELEGRAM", "EMAIL").equals(response.getData().getMfaMethods())
                        && "select-token".equals(response.getData().getMfaSelectionToken())
                        && response.getData().getAccessToken() == null
                        && response.getData().getMfaChallengeToken() == null)
                .verifyComplete();
        verify(userServiceClient, never()).startEmailMfaLogin(anyLong());
        verify(userServiceClient, never()).startTelegramMfaLogin(anyLong(), any());
    }

    @Test
    void withOnlyOneMethodTheCodeIsSentStraightAway() {
        givenUserWithPassword("pass");
        givenMfa(true, false);
        when(jwtUtils.generateMfaSelectionToken("user")).thenReturn("select-token");
        when(userServiceClient.startTelegramMfaLogin(1L, "user"))
                .thenReturn(Mono.just(new TelegramMfaChallenge("tg-challenge", Instant.now().plusSeconds(300))));
        AuthenticationService service = new AuthenticationService(jwtUtils, userServiceClient, serviceClientFactory, storeImageUrlResolver);

        StepVerifier.create(service.authenticate(new AuthRequest("user", "pass"), ClientChannel.WEB))
                .expectNextMatches(response -> "TELEGRAM".equals(response.getData().getMfaMethod())
                        && "tg-challenge".equals(response.getData().getMfaChallengeToken())
                        && List.of("TELEGRAM").equals(response.getData().getMfaMethods()))
                .verifyComplete();
        verify(userServiceClient, never()).startEmailMfaLogin(anyLong());
    }

    @Test
    void sendMfaCodeSendsToTheChosenMethod() {
        givenUserWithPassword("pass");
        givenMfa(true, true);
        when(jwtUtils.validateMfaSelectionToken("select-token", "user")).thenReturn(true);
        when(userServiceClient.startTelegramMfaLogin(1L, "user"))
                .thenReturn(Mono.just(new TelegramMfaChallenge("tg-challenge", Instant.now().plusSeconds(300))));
        when(userServiceClient.startEmailMfaLogin(1L))
                .thenReturn(Mono.just(new EmailMfaChallenge("mail-challenge", Instant.now().plusSeconds(300), "us•••@example.com")));
        AuthenticationService service = new AuthenticationService(jwtUtils, userServiceClient, serviceClientFactory, storeImageUrlResolver);

        StepVerifier.create(service.sendMfaCode("user", "select-token", "TELEGRAM", ClientChannel.WEB))
                .expectNextMatches(response -> "TELEGRAM".equals(response.getData().getMfaMethod())
                        && "tg-challenge".equals(response.getData().getMfaChallengeToken())
                        && "select-token".equals(response.getData().getMfaSelectionToken())
                        && List.of("TELEGRAM", "EMAIL").equals(response.getData().getMfaMethods()))
                .verifyComplete();
        StepVerifier.create(service.sendMfaCode("user", "select-token", "EMAIL", ClientChannel.WEB))
                .expectNextMatches(response -> "EMAIL".equals(response.getData().getMfaMethod())
                        && "us•••@example.com".equals(response.getData().getMfaDestination()))
                .verifyComplete();
    }

    @Test
    void sendMfaCodeRejectsAnExpiredSelectionOrAMethodThatIsOff() {
        givenUserWithPassword("pass");
        givenMfa(true, false);
        when(jwtUtils.validateMfaSelectionToken("old-token", "user")).thenReturn(false);
        when(jwtUtils.validateMfaSelectionToken("select-token", "user")).thenReturn(true);
        AuthenticationService service = new AuthenticationService(jwtUtils, userServiceClient, serviceClientFactory, storeImageUrlResolver);

        StepVerifier.create(service.sendMfaCode("user", "old-token", "TELEGRAM", ClientChannel.WEB))
                .expectError(BadCredentialsException.class)
                .verify();
        StepVerifier.create(service.sendMfaCode("user", "select-token", "EMAIL", ClientChannel.WEB))
                .expectError(BadCredentialsException.class)
                .verify();
        verify(userServiceClient, never()).startEmailMfaLogin(anyLong());
        verify(userServiceClient, never()).startTelegramMfaLogin(anyLong(), any());
    }

    private void givenUserWithPassword(String password) {
        var credentials = credentials(passwordEncoder.encode(password), Set.of(), Set.of(21L));
        when(userServiceClient.findByUsername("user")).thenReturn(Mono.just(credentials));
        when(userServiceClient.findByUsername("user", 12L)).thenReturn(Mono.just(credentials));
    }

    private void givenWorkspace(String businessExtra, String storeExtra) {
        when(serviceClientFactory.forService("business", "/api/v1/businesses")).thenReturn(jsonClient("""
                {"status":1,"code":200,"data":[{"id":12,"ownerUserId":1,"name":"Demo Business","code":"DEMO","defaultCurrencyCode":"USD","status":1%s}]}
                """.formatted(businessExtra)));
        when(serviceClientFactory.forService("store", "/api/v1/stores")).thenReturn(jsonClient("""
                {"status":1,"code":200,"data":{"content":[{"id":21,"businessId":12,"name":"Main Store","code":"MAIN","status":1%s}],"page":0,"size":100,"totalElements":1,"totalPages":1}}
                """.formatted(storeExtra)));
    }

    private static WebClient jsonClient(String body) {
        return WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "application/json").body(body).build()))
                .build();
    }

    @Test
    void suspendedMerchantIsRefusedWithTheReasonBeforeAnyCodeIsSent() {
        givenUserWithPassword("pass");
        givenMfa(false, true);
        givenWorkspace(",\"suspendedAt\":\"2026-10-19T10:00:00\",\"suspensionReason\":\"Unpaid platform fees\"", "");
        AuthenticationService service = new AuthenticationService(jwtUtils, userServiceClient, serviceClientFactory, storeImageUrlResolver);

        StepVerifier.create(service.authenticate(new AuthRequest("user", "pass"), ClientChannel.WEB))
                .expectErrorSatisfies(error -> {
                    var status = (org.springframework.web.server.ResponseStatusException) error;
                    assertEquals(HttpStatus.FORBIDDEN, status.getStatusCode());
                    org.junit.jupiter.api.Assertions.assertTrue(status.getReason().contains("Unpaid platform fees"));
                })
                .verify();
        verify(userServiceClient, never()).startEmailMfaLogin(anyLong());
    }

    @Test
    void staffOfOnlySuspendedStoresAreRefusedButTheOwnerIsNot() {
        var staff = new com.x.bff.dto.UserCredentialsResponse(5L, "user", passwordEncoder.encode("pass"), Set.of());
        staff.setBusinessIds(Set.of(12L));
        staff.setStoreIds(Set.of(21L));
        when(userServiceClient.findByUsername("user")).thenReturn(Mono.just(staff));
        when(userServiceClient.findByUsername("user", 12L)).thenReturn(Mono.just(staff));
        givenWorkspace("", ",\"suspendedAt\":\"2026-10-19T10:00:00\",\"suspensionReason\":\"Counterfeit goods\"");
        AuthenticationService service = new AuthenticationService(jwtUtils, userServiceClient, serviceClientFactory, storeImageUrlResolver);

        StepVerifier.create(service.authenticate(new AuthRequest("user", "pass"), ClientChannel.WEB))
                .expectErrorSatisfies(error -> org.junit.jupiter.api.Assertions.assertTrue(
                        ((org.springframework.web.server.ResponseStatusException) error).getReason().contains("Your store Main Store is suspended")))
                .verify();

        givenUserWithPassword("pass");
        when(jwtUtils.generateToken(any(), any(), any(), any())).thenReturn("access");
        when(jwtUtils.generateRefreshToken(any(), any())).thenReturn("refresh");
        StepVerifier.create(service.authenticate(new AuthRequest("user", "pass"), ClientChannel.WEB))
                .expectNextMatches(response -> response.getData().getUser() != null)
                .verifyComplete();
    }

    private void givenMfa(boolean telegram, boolean email) {
        when(userServiceClient.telegramMfaStatus(anyLong()))
                .thenReturn(Mono.just(new TelegramMfaStatus(true, "bot", telegram, telegram, false)));
        when(userServiceClient.emailMfaStatus(anyLong()))
                .thenReturn(Mono.just(new EmailMfaStatus(true, true, email, email, "us•••@example.com")));
    }

    @Test
    void authenticateReturnsTokensForValidBcryptCredentials() {
        String hashed = passwordEncoder.encode("pass");
        var credentials = credentials(hashed, Set.of("x-product:read"), Set.of(21L));
        when(userServiceClient.findByUsername("user")).thenReturn(Mono.just(credentials));
        when(userServiceClient.findByUsername("user", 12L)).thenReturn(Mono.just(credentials));
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
        var credentials = credentials(hashed, Set.of("x-product:read"), Set.of(21L));
        when(userServiceClient.findByUsername("user")).thenReturn(Mono.just(credentials));
        when(userServiceClient.findByUsername("user", 12L)).thenReturn(Mono.just(credentials));
        when(jwtUtils.generateToken(any(), any(), any(), anyLong())).thenReturn("access2");
        when(jwtUtils.generateRefreshToken(any(), any())).thenReturn("refresh2");
        when(jwtUtils.getAccessExpirationSeconds()).thenReturn(900L);

        AuthenticationService service = new AuthenticationService(jwtUtils, userServiceClient, serviceClientFactory, storeImageUrlResolver);

        StepVerifier.create(service.refresh("refresh-jwt", ClientChannel.WEB))
                .expectNextMatches(response -> "access2".equals(response.getData().getAccessToken())
                        && "web".equals(response.getData().getChannel()))
                .verifyComplete();
    }

    @Test
    void failedRegistrationRemovesEverythingItCreatedEvenWhenOneRemovalFails() {
        List<String> calls = new CopyOnWriteArrayList<>();
        givenWorkspaceServices(
                calls,
                request -> isPost(request) ? created(NEW_BUSINESS) : noContent(),
                request -> isPost(request) ? created(NEW_STORE) : failure(HttpStatus.INTERNAL_SERVER_ERROR),
                request -> isPost(request) ? failure(HttpStatus.BAD_GATEWAY) : noContent());
        when(userServiceClient.register(any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(new UserServiceClient.RegisteredUser(1L)));
        when(userServiceClient.deleteFailedRegistration("owner", 12L)).thenReturn(Mono.empty());

        AuthenticationService service = new AuthenticationService(jwtUtils, userServiceClient, serviceClientFactory, storeImageUrlResolver);

        StepVerifier.create(service.register(registration(), ClientChannel.WEB))
                .expectErrorMatches(error -> error instanceof WebClientResponseException response
                        && response.getStatusCode().value() == 502)
                .verify();
        assertEquals(List.of(
                "POST http://business/api/v1/businesses",
                "POST http://store/api/v1/stores",
                "POST http://customer/internal/marketplace/customer",
                "DELETE http://customer/internal/marketplace/customer?userId=1&businessId=12",
                "DELETE http://store/api/v1/stores/21/registration-failure",
                "DELETE http://business/api/v1/businesses/12/registration-failure"), calls);
        verify(userServiceClient).deleteFailedRegistration("owner", 12L);
    }

    @Test
    void takenStoreCodeRemovesTheNewBusinessAndAccount() {
        List<String> calls = new CopyOnWriteArrayList<>();
        givenWorkspaceServices(
                calls,
                request -> isPost(request) ? created(NEW_BUSINESS) : noContent(),
                request -> failure(HttpStatus.CONFLICT),
                request -> noContent());
        when(userServiceClient.register(any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(new UserServiceClient.RegisteredUser(1L)));
        when(userServiceClient.deleteFailedRegistration("owner", 12L)).thenReturn(Mono.empty());

        AuthenticationService service = new AuthenticationService(jwtUtils, userServiceClient, serviceClientFactory, storeImageUrlResolver);

        StepVerifier.create(service.register(registration(), ClientChannel.WEB))
                .expectError(WebClientResponseException.Conflict.class)
                .verify();
        assertEquals(List.of(
                "POST http://business/api/v1/businesses",
                "POST http://store/api/v1/stores",
                "DELETE http://business/api/v1/businesses/12/registration-failure"), calls);
        verify(userServiceClient).deleteFailedRegistration("owner", 12L);
    }

    @Test
    void registrationSubmitsAnApplicationWithoutASession() {
        List<String> calls = new CopyOnWriteArrayList<>();
        givenWorkspaceServices(
                calls,
                request -> isPost(request) ? created(NEW_BUSINESS) : noContent(),
                request -> isPost(request) ? created(NEW_STORE) : noContent(),
                request -> created("{}"));
        when(userServiceClient.register(any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(new UserServiceClient.RegisteredUser(1L)));
        when(userServiceClient.ensureOwnerBusinessAccess(anyLong(), anyLong(), any())).thenReturn(Mono.empty());
        when(userServiceClient.sendNotice(eq(1L), any(), any(), any())).thenReturn(Mono.empty());
        AuthenticationService service = new AuthenticationService(jwtUtils, userServiceClient, serviceClientFactory, storeImageUrlResolver);

        StepVerifier.create(service.register(registration(), ClientChannel.WEB))
                .expectNextMatches(response -> response.getCode() == 202
                        && "PENDING_REVIEW".equals(response.getData().getApplicationStatus())
                        && response.getData().getAccessToken() == null
                        && response.getData().getUser() == null
                        && "Main Store".equals(response.getData().getStores().get(0).name()))
                .verifyComplete();
        verify(userServiceClient).sendNotice(eq(1L), eq("We received your merchant application"), any(), any());
        verify(jwtUtils, never()).generateToken(any(), any(), any(), any());
    }

    @Test
    void pendingApplicantCannotSignInYet() {
        givenUserWithPassword("pass");
        givenMfa(false, false);
        givenWorkspace(",\"status\":0", "");
        AuthenticationService service = new AuthenticationService(jwtUtils, userServiceClient, serviceClientFactory, storeImageUrlResolver);

        StepVerifier.create(service.authenticate(new AuthRequest("user", "pass"), ClientChannel.WEB))
                .expectErrorSatisfies(error -> {
                    var status = (org.springframework.web.server.ResponseStatusException) error;
                    assertEquals(HttpStatus.FORBIDDEN, status.getStatusCode());
                    org.junit.jupiter.api.Assertions.assertTrue(status.getReason().contains("is under review"));
                })
                .verify();
    }

    @Test
    void takenUsernameRemovesNothing() {
        List<String> calls = new CopyOnWriteArrayList<>();
        givenWorkspaceServices(calls, request -> noContent(), request -> noContent(), request -> noContent());
        when(userServiceClient.register(any(), any(), any(), any(), any()))
                .thenReturn(Mono.error(WebClientResponseException.create(409, "Conflict", HttpHeaders.EMPTY, new byte[0], null)));

        AuthenticationService service = new AuthenticationService(jwtUtils, userServiceClient, serviceClientFactory, storeImageUrlResolver);

        StepVerifier.create(service.register(registration(), ClientChannel.WEB))
                .expectError(WebClientResponseException.Conflict.class)
                .verify();
        assertEquals(List.of(), calls);
        verify(userServiceClient, never()).deleteFailedRegistration(any(), any());
    }

    private static final String NEW_BUSINESS = """
            {"id":12,"ownerUserId":1,"name":"Demo Business","code":"DEMO","defaultCurrencyCode":"USD","status":1}""";

    private static final String NEW_STORE = """
            {"id":21,"businessId":12,"name":"Main Store","code":"MAIN","status":1}""";

    private void givenWorkspaceServices(
            List<String> calls,
            Function<ClientRequest, ClientResponse> business,
            Function<ClientRequest, ClientResponse> store,
            Function<ClientRequest, ClientResponse> customer) {
        when(serviceClientFactory.forService("business", "/api/v1/businesses"))
                .thenReturn(recordingClient("http://business/api/v1/businesses", calls, business));
        when(serviceClientFactory.forService("store", "/api/v1/stores"))
                .thenReturn(recordingClient("http://store/api/v1/stores", calls, store));
        when(serviceClientFactory.forService("customer", "/internal/marketplace"))
                .thenReturn(recordingClient("http://customer/internal/marketplace", calls, customer));
    }

    private static WebClient recordingClient(
            String baseUrl, List<String> calls, Function<ClientRequest, ClientResponse> responder) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .exchangeFunction(request -> {
                    calls.add(request.method() + " " + request.url());
                    return Mono.just(responder.apply(request));
                })
                .build();
    }

    private static boolean isPost(ClientRequest request) {
        return HttpMethod.POST.equals(request.method());
    }

    private static ClientResponse created(String data) {
        return ClientResponse.create(HttpStatus.CREATED)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body("{\"status\":1,\"code\":201,\"data\":" + data + "}")
                .build();
    }

    private static ClientResponse failure(HttpStatus status) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body("{\"status\":0,\"code\":" + status.value() + ",\"message\":\"" + status.getReasonPhrase() + "\"}")
                .build();
    }

    private static ClientResponse noContent() {
        return ClientResponse.create(HttpStatus.NO_CONTENT).build();
    }

    private static RegistrationRequest registration() {
        return new RegistrationRequest(
                "Shop Owner", "owner", "password123", "owner@example.com", "012345678",
                "Demo Business", "DEMO", "USD", null, null, true, "Asia/Phnom_Penh", 1,
                "Main Store", "MAIN", "Street 1", "Phnom Penh", "KH", null, null, null);
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

    private static com.x.bff.dto.UserCredentialsResponse credentials(
            String password,
            Set<String> permissions,
            Set<Long> storeIds) {
        var credentials = new com.x.bff.dto.UserCredentialsResponse(1L, "user", password, permissions);
        credentials.setBusinessIds(Set.of(12L));
        credentials.setStoreIds(storeIds);
        return credentials;
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
