package com.x.bff.service;

import com.x.bff.dto.AuthRequest;
import com.x.bff.dto.AuthResponse;
import com.x.bff.dto.AuthUserSummary;
import com.x.bff.dto.BusinessResponse;
import com.x.bff.dto.CreateBusinessRequest;
import com.x.bff.dto.CreateStoreRequest;
import com.x.bff.dto.RegistrationRequest;
import com.x.bff.dto.StoreResponse;
import com.x.bff.dto.UserCredentialsResponse;
import com.x.bff.security.ClientChannel;
import com.x.bff.security.JwtUtils;
import com.sharedlib.response.ApiResponse;
import com.sharedlib.response.PageResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class AuthenticationService {

    private final JwtUtils jwtUtils;
    private final UserServiceClient userServiceClient;
    private final WebClient businessClient;
    private final WebClient storeClient;
    private final StoreImageUrlResolver storeImageUrlResolver;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthenticationService(
            JwtUtils jwtUtils,
            UserServiceClient userServiceClient,
            ServiceClientFactory clientFactory,
            StoreImageUrlResolver storeImageUrlResolver) {
        this.jwtUtils = jwtUtils;
        this.userServiceClient = userServiceClient;
        this.businessClient = clientFactory.forService("business", "/api/v1/businesses");
        this.storeClient = clientFactory.forService("store", "/api/v1/stores");
        this.storeImageUrlResolver = storeImageUrlResolver;
    }

    public Mono<ApiResponse<AuthResponse>> authenticate(AuthRequest request, ClientChannel channel) {
        return findUser(request.getUsername())
                .filter(user -> passwordMatches(request.getPassword(), user.getPassword()))
                .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid username or password")))
                .flatMap(user -> createTokenResponseWithBusiness(user, channel));
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
                    .flatMap(user -> createTokenResponseWithBusiness(user, channel));
        } catch (RuntimeException ex) {
            return Mono.error(new BadCredentialsException("Invalid refresh token"));
        }
    }

    public Mono<ApiResponse<AuthResponse>> register(RegistrationRequest request, ClientChannel channel) {
        return userServiceClient.register(
                        request.fullName(), request.username(), request.password(), request.email(), request.phone())
                .flatMap(user -> createBusiness(user.id(), request)
                        .flatMap(business -> createStore(business.id(), request)
                                .flatMap(store -> userServiceClient.assignOwnerStoreMembership(user.id(), store.id())
                                        .then(authenticate(new AuthRequest(request.username(), request.password()), channel)))));
    }

    private Mono<BusinessResponse> createBusiness(Long userId, RegistrationRequest request) {
        return businessClient.post()
                .bodyValue(new CreateBusinessCommand(
                        userId, request.businessName(), request.businessCode(), request.defaultCurrencyCode(),
                        request.taxRegistrationNumber(), request.taxRegistrationLabel(), request.pricesIncludeTax(),
                        request.timeZone(), request.fiscalYearStartMonth()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<BusinessResponse>>() {})
                .map(ApiResponse::getData);
    }

    private Mono<StoreResponse> createStore(Long businessId, RegistrationRequest request) {
        return storeClient.post()
                .bodyValue(new CreateStoreRequest(
                        businessId, request.storeName(), request.storeCode(), request.storeAddressLine1(), null, null,
                        request.storeCity(), null, request.storeCountryCode(), null, request.phone(), null,
                        request.email(), null, request.storeLatitude(), request.storeLongitude(), List.of()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<StoreResponse>>() {})
                .map(ApiResponse::getData);
    }

    private record CreateBusinessCommand(
            Long ownerUserId, String name, String code, String defaultCurrencyCode,
            String taxRegistrationNumber, String taxRegistrationLabel, Boolean pricesIncludeTax,
            String timeZone, Integer fiscalYearStartMonth) {
    }

    private Mono<UserCredentialsResponse> findUser(String username) {
        return userServiceClient.findByUsername(username)
                .onErrorResume(
                        org.springframework.web.reactive.function.client.WebClientResponseException.NotFound.class,
                        ex -> Mono.empty());
    }

    private Mono<ApiResponse<AuthResponse>> createTokenResponseWithBusiness(
            UserCredentialsResponse user,
            ClientChannel channel) {
        return findPrimaryBusiness(user.getId())
                .flatMap(business -> findStores(business.id())
                        .map(stores -> createTokenResponse(user, channel, business, stores)))
                .switchIfEmpty(Mono.fromSupplier(() -> createTokenResponse(user, channel, null, List.of())));
    }

    private Mono<BusinessResponse> findPrimaryBusiness(Long userId) {
        return businessClient.get()
                .uri(uriBuilder -> uriBuilder.queryParam("ownerUserId", userId).build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<List<BusinessResponse>>>() {})
                .map(ApiResponse::getData)
                .flatMapIterable(businesses -> businesses == null ? List.of() : businesses)
                .next();
    }

    private Mono<List<StoreResponse>> findStores(Long businessId) {
        return storeClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("businessId", businessId)
                        .queryParam("page", 0)
                        .queryParam("size", 100)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<PageResponse<StoreResponse>>>() {})
                .map(ApiResponse::getData)
                .flatMap(storeImageUrlResolver::resolvePage)
                .map(page -> page == null || page.content() == null ? List.of() : page.content());
    }

    private ApiResponse<AuthResponse> createTokenResponse(
            UserCredentialsResponse user,
            ClientChannel channel,
            BusinessResponse business,
            List<StoreResponse> stores) {
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
                .business(business)
                .stores(stores)
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
