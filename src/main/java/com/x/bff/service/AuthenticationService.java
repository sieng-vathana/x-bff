package com.x.bff.service;

import com.x.bff.dto.AuthRequest;
import com.x.bff.dto.AuthResponse;
import com.x.bff.dto.AuthUserSummary;
import com.x.bff.dto.UserCredentialsResponse;
import com.x.bff.security.ClientChannel;
import com.x.bff.security.JwtUtils;
import com.sharedlib.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Set;

@Service
public class AuthenticationService {

    private final JwtUtils jwtUtils;
    private final UserServiceClient userServiceClient;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthenticationService(JwtUtils jwtUtils, UserServiceClient userServiceClient) {
        this.jwtUtils = jwtUtils;
        this.userServiceClient = userServiceClient;
    }

    public Mono<ApiResponse<AuthResponse>> authenticate(AuthRequest request, ClientChannel channel) {
        return findUser(request.getUsername())
                .filter(user -> passwordMatches(request.getPassword(), user.getPassword()))
                .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid username or password")))
                .map(user -> createTokenResponse(user, channel));
    }

    public Mono<ApiResponse<AuthResponse>> refresh(String refreshToken, ClientChannel channel) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Mono.error(new BadCredentialsException("Invalid refresh token"));
        }
        try {
            String username = jwtUtils.extractUsername(refreshToken);
            if (!jwtUtils.validateRefreshToken(refreshToken, username)) {
                return Mono.error(new BadCredentialsException("Invalid refresh token"));
            }

            return findUser(username)
                    .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid refresh token")))
                    .map(user -> createTokenResponse(user, channel));
        } catch (RuntimeException ex) {
            return Mono.error(new BadCredentialsException("Invalid refresh token"));
        }
    }

    private Mono<UserCredentialsResponse> findUser(String username) {
        return userServiceClient.findByUsername(username)
                .onErrorResume(
                        org.springframework.web.reactive.function.client.WebClientResponseException.NotFound.class,
                        ex -> Mono.empty());
    }

    private ApiResponse<AuthResponse> createTokenResponse(
            UserCredentialsResponse user,
            ClientChannel channel) {
        Set<String> permissions = user.getPermissions() == null
                ? Collections.emptySet()
                : user.getPermissions();
        String channelValue = channel.wireValue();
        AuthResponse response = AuthResponse.builder()
                .accessToken(jwtUtils.generateToken(
                        user.getUsername(), permissions, channelValue, user.getId()))
                .refreshToken(jwtUtils.generateRefreshToken(user.getUsername(), channelValue))
                .expiresIn(jwtUtils.getAccessExpirationSeconds())
                .tokenType("Bearer")
                .channel(channelValue)
                .user(AuthUserSummary.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .permissions(permissions)
                        .build())
                .build();
        return ApiResponse.success(HttpStatus.OK.value(), response);
    }

    /**
     * Accepts only BCrypt-encoded stored passwords. Plaintext comparison is intentionally rejected.
     */
    private boolean passwordMatches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null || storedPassword.isBlank()) {
            return false;
        }
        if (!(storedPassword.startsWith("$2a$")
                || storedPassword.startsWith("$2b$")
                || storedPassword.startsWith("$2y$"))) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, storedPassword);
    }
}
