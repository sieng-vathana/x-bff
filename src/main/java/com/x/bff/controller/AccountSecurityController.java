package com.x.bff.controller;

import com.sharedlib.response.ApiResponse;
import com.x.bff.dto.TelegramMfaLink;
import com.x.bff.dto.TelegramMfaManageRequest;
import com.x.bff.dto.TelegramMfaStatus;
import com.x.bff.dto.UserCredentialsResponse;
import com.x.bff.service.UserServiceClient;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth/security/telegram-mfa")
public class AccountSecurityController {
    private final UserServiceClient userServiceClient;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AccountSecurityController(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<TelegramMfaStatus>>> status(Authentication authentication) {
        return currentUser(authentication)
                .flatMap(user -> userServiceClient.telegramMfaStatus(user.getId()))
                .map(status -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), status)));
    }

    @PostMapping("/link")
    public Mono<ResponseEntity<ApiResponse<TelegramMfaLink>>> createLink(Authentication authentication) {
        return currentUser(authentication)
                .flatMap(user -> userServiceClient.createTelegramMfaLink(user.getId()))
                .map(link -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), link)));
    }

    @PutMapping
    public Mono<ResponseEntity<ApiResponse<TelegramMfaStatus>>> setEnabled(
            @Valid @RequestBody TelegramMfaManageRequest request, Authentication authentication) {
        if (request.enabled() == null) {
            return Mono.error(new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "enabled is required"));
        }
        return currentUser(authentication)
                .flatMap(user -> {
                    requirePassword(request.currentPassword(), user);
                    return userServiceClient.setTelegramMfaEnabled(user.getId(), request.enabled());
                })
                .map(status -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), status)));
    }

    @PostMapping("/disconnect")
    public Mono<ResponseEntity<ApiResponse<TelegramMfaStatus>>> disconnect(
            @Valid @RequestBody TelegramMfaManageRequest request, Authentication authentication) {
        return currentUser(authentication)
                .flatMap(user -> {
                    requirePassword(request.currentPassword(), user);
                    return userServiceClient.disconnectTelegramMfa(user.getId());
                })
                .map(status -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), status)));
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
