package com.x.bff.service;

import com.x.bff.dto.AuthRequest;
import com.x.bff.security.ClientChannel;
import com.x.bff.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        jwtUtils = mock(JwtUtils.class);
        userServiceClient = mock(UserServiceClient.class);
    }

    @Test
    void authenticateReturnsTokensForValidBcryptCredentials() {
        String hashed = passwordEncoder.encode("pass");
        when(userServiceClient.findByUsername("user")).thenReturn(Mono.just(
                new com.x.bff.dto.UserCredentialsResponse(
                        1L, "user", hashed, Set.of("VIEW_PRODUCTS"))));
        when(jwtUtils.generateToken(eq("user"), eq(Set.of("VIEW_PRODUCTS")), eq("mobile"), eq(1L)))
                .thenReturn("access");
        when(jwtUtils.generateRefreshToken(eq("user"), eq("mobile"))).thenReturn("refresh");
        when(jwtUtils.getAccessExpirationSeconds()).thenReturn(900L);

        AuthenticationService service = new AuthenticationService(jwtUtils, userServiceClient);

        StepVerifier.create(service.authenticate(new AuthRequest("user", "pass"), ClientChannel.MOBILE))
                .expectNextMatches(response -> response.getCode() == 200
                        && "access".equals(response.getData().getAccessToken())
                        && "refresh".equals(response.getData().getRefreshToken())
                        && "mobile".equals(response.getData().getChannel())
                        && Long.valueOf(900L).equals(response.getData().getExpiresIn())
                        && "user".equals(response.getData().getUser().getUsername()))
                .verifyComplete();
    }

    @Test
    void authenticateRejectsPlaintextStoredPassword() {
        when(userServiceClient.findByUsername("user")).thenReturn(Mono.just(
                new com.x.bff.dto.UserCredentialsResponse(
                        1L, "user", "plaintext-pass", Set.of())));

        AuthenticationService service = new AuthenticationService(jwtUtils, userServiceClient);

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

        AuthenticationService service = new AuthenticationService(jwtUtils, userServiceClient);

        StepVerifier.create(service.authenticate(new AuthRequest("user", "wrong"), ClientChannel.WEB))
                .expectError(BadCredentialsException.class)
                .verify();
    }

    @Test
    void refreshRejectsBlankToken() {
        AuthenticationService service = new AuthenticationService(jwtUtils, userServiceClient);

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
                        1L, "user", hashed, Set.of("VIEW_PRODUCTS"))));
        when(jwtUtils.generateToken(any(), any(), any(), anyLong())).thenReturn("access2");
        when(jwtUtils.generateRefreshToken(any(), any())).thenReturn("refresh2");
        when(jwtUtils.getAccessExpirationSeconds()).thenReturn(900L);

        AuthenticationService service = new AuthenticationService(jwtUtils, userServiceClient);

        StepVerifier.create(service.refresh("refresh-jwt", ClientChannel.WEB))
                .expectNextMatches(response -> "access2".equals(response.getData().getAccessToken())
                        && "web".equals(response.getData().getChannel()))
                .verifyComplete();
    }
}
