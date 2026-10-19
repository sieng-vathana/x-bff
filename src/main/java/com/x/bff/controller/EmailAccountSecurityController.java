package com.x.bff.controller;

import com.sharedlib.response.ApiResponse;
import com.x.bff.dto.EmailMfaChallenge;
import com.x.bff.dto.EmailMfaManageRequest;
import com.x.bff.dto.EmailMfaSetupVerifyRequest;
import com.x.bff.dto.EmailMfaStatus;
import com.x.bff.dto.UserCredentialsResponse;
import com.x.bff.service.UserServiceClient;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth/security/email-mfa")
public class EmailAccountSecurityController {
    private final UserServiceClient userServiceClient;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public EmailAccountSecurityController(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<EmailMfaStatus>>> status(Authentication authentication) {
        return currentUser(authentication).flatMap(user -> userServiceClient.emailMfaStatus(user.getId()))
                .map(status -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), status)));
    }

    @PostMapping("/verification")
    public Mono<ResponseEntity<ApiResponse<EmailMfaChallenge>>> startVerification(
            @Valid @RequestBody EmailMfaManageRequest request, Authentication authentication) {
        return currentUser(authentication).flatMap(user -> {
            requirePassword(request.currentPassword(), user);
            return userServiceClient.startEmailMfaSetup(user.getId());
        }).map(challenge -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), challenge)));
    }

    @PostMapping("/verification/confirm")
    public Mono<ResponseEntity<ApiResponse<EmailMfaStatus>>> confirmVerification(
            @Valid @RequestBody EmailMfaSetupVerifyRequest request, Authentication authentication) {
        return currentUser(authentication).flatMap(user ->
                userServiceClient.verifyEmailMfaSetup(user.getId(), request.challengeToken(), request.code())
                        .flatMap(verified -> verified
                                ? userServiceClient.emailMfaStatus(user.getId())
                                : Mono.error(new org.springframework.web.server.ResponseStatusException(
                                        HttpStatus.BAD_REQUEST, "The email verification code is invalid or expired"))))
                .map(status -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), status)));
    }

    @PutMapping
    public Mono<ResponseEntity<ApiResponse<EmailMfaStatus>>> setEnabled(
            @Valid @RequestBody EmailMfaManageRequest request, Authentication authentication) {
        if (request.enabled() == null) {
            return Mono.error(new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "enabled is required"));
        }
        return currentUser(authentication).flatMap(user -> {
            requirePassword(request.currentPassword(), user);
            return userServiceClient.setEmailMfaEnabled(user.getId(), request.enabled());
        }).map(status -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), status)));
    }

    @PostMapping("/disconnect")
    public Mono<ResponseEntity<ApiResponse<EmailMfaStatus>>> disconnect(
            @Valid @RequestBody EmailMfaManageRequest request, Authentication authentication) {
        return currentUser(authentication).flatMap(user -> {
            requirePassword(request.currentPassword(), user);
            return userServiceClient.disconnectEmailMfa(user.getId());
        }).map(status -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), status)));
    }

    private Mono<UserCredentialsResponse> currentUser(Authentication authentication) {
        return userServiceClient.findByUsername(authentication.getName());
    }

    private void requirePassword(String rawPassword, UserCredentialsResponse user) {
        String stored = user.getPassword();
        if (rawPassword == null || stored == null || !isBcrypt(stored) || !passwordEncoder.matches(rawPassword, stored)) {
            throw new BadCredentialsException("Current password is incorrect");
        }
    }

    private static boolean isBcrypt(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }
}
