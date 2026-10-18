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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final JwtUtils jwtUtils;
    private final UserServiceClient userServiceClient;
    private final WebClient businessClient;
    private final WebClient storeClient;
    private final WebClient customerClient;
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
        this.customerClient = clientFactory.forService("customer", "/internal/marketplace");
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

    /**
     * Creates the owner account, business, first store and marketplace profile, then signs in.
     * When a step fails, the records created before it are removed again, so the username,
     * business code and store code are free for the next attempt.
     */
    public Mono<ApiResponse<AuthResponse>> register(RegistrationRequest request, ClientChannel channel) {
        return Mono.defer(() -> {
            CreatedRecords created = new CreatedRecords();
            return userServiceClient.register(
                            request.fullName(), request.username(), request.password(), request.email(), request.phone())
                    .doOnNext(user -> created.userId = user.id())
                    .flatMap(user -> createBusiness(user.id(), request))
                    .doOnNext(business -> created.businessId = business.id())
                    .flatMap(business -> createStore(business.id(), request))
                    .doOnNext(store -> created.storeId = store.id())
                    .flatMap(store -> createMarketplaceCustomer(created.userId, created.businessId, store.id(), request)
                            .then(userServiceClient.ensureOwnerBusinessAccess(
                                    created.userId, created.businessId, List.of(store.id())))
                            .thenReturn(store))
                    .switchIfEmpty(Mono.error(() -> new IllegalStateException("Registration did not complete")))
                    .onErrorResume(error -> undoRegistration(request.username(), created).then(Mono.error(error)))
                    .flatMap(store -> authenticate(new AuthRequest(request.username(), request.password()), channel));
        });
    }

    private Mono<Void> createMarketplaceCustomer(
            Long userId, Long businessId, Long storeId, RegistrationRequest request) {
        return customerClient.post()
                .uri("/customer")
                .bodyValue(new MarketplaceCustomerCommand(
                        userId, businessId, storeId, request.fullName(), request.phone(), request.email()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<Object>>() {})
                .then();
    }

    /** Removes what a failed registration created, newest first; each removal runs even if an earlier one fails. */
    private Mono<Void> undoRegistration(String username, CreatedRecords created) {
        List<Mono<Void>> removals = new ArrayList<>();
        if (created.storeId != null) {
            removals.add(undoStep("marketplace customer", customerClient.delete()
                    .uri("/customer?userId={userId}&businessId={businessId}", created.userId, created.businessId)
                    .retrieve()
                    .toBodilessEntity()));
            removals.add(undoStep("store", storeClient.delete()
                    .uri("/{id}/registration-failure", created.storeId)
                    .retrieve()
                    .toBodilessEntity()));
        }
        if (created.businessId != null) {
            removals.add(undoStep("business", businessClient.delete()
                    .uri("/{id}/registration-failure", created.businessId)
                    .retrieve()
                    .toBodilessEntity()));
        }
        if (created.userId != null) {
            removals.add(undoStep("account", userServiceClient.deleteFailedRegistration(username, created.businessId)));
        }
        return Flux.concat(removals).then();
    }

    private Mono<Void> undoStep(String record, Mono<?> removal) {
        return removal.then()
                .doOnSuccess(ignored -> log.info("Removed the {} of a failed registration", record))
                .onErrorResume(error -> {
                    log.warn("Could not remove the {} of a failed registration: {}", record, error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<BusinessResponse> createBusiness(Long userId, RegistrationRequest request) {
        return businessClient.post()
                .bodyValue(new CreateBusinessCommand(
                        userId, request.businessName(), request.businessCode(), request.defaultCurrencyCode(),
                        request.taxRegistrationNumber(), request.taxRegistrationLabel(), request.pricesIncludeTax(),
                        request.timeZone(), request.fiscalYearStartMonth(), request.usdToKhrExchangeRate()))
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
            String timeZone, Integer fiscalYearStartMonth, java.math.BigDecimal usdToKhrExchangeRate) {
    }

    private record MarketplaceCustomerCommand(
            Long userId, Long businessId, Long storeId, String fullName, String phone, String email) {
    }

    /** What a registration has created so far, so a later failure can remove it again. */
    private static final class CreatedRecords {
        private Long userId;
        private Long businessId;
        private Long storeId;
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
        return findPrimaryBusiness(user)
                .flatMap(business -> findStores(business.id())
                        .flatMap(stores -> ensureOwnerAccessWhenApplicable(user, business, stores)
                                .then(userServiceClient.findByUsername(user.getUsername(), business.id()))
                                .map(scopedUser -> createTokenResponse(
                                        scopedUser,
                                        channel,
                                        business,
                                        stores.stream()
                                                .filter(store -> scopedUser.getStoreIds() != null
                                                        && scopedUser.getStoreIds().contains(store.id()))
                                                .toList()))))
                .switchIfEmpty(Mono.fromSupplier(() -> createTokenResponse(user, channel, null, List.of())));
    }

    private Mono<BusinessResponse> findPrimaryBusiness(UserCredentialsResponse user) {
        Mono<BusinessResponse> ownedBusiness = businessClient.get()
                .uri(uriBuilder -> uriBuilder.queryParam("ownerUserId", user.getId()).build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<List<BusinessResponse>>>() {})
                .map(ApiResponse::getData)
                .flatMapIterable(businesses -> businesses == null ? List.of() : businesses)
                .next();
        Mono<BusinessResponse> membershipBusiness = Mono.justOrEmpty(user.getBusinessIds())
                .flatMapIterable(ids -> ids.stream().sorted().toList())
                .next()
                .flatMap(this::findBusinessById);
        return ownedBusiness.switchIfEmpty(membershipBusiness);
    }

    private Mono<BusinessResponse> findBusinessById(Long businessId) {
        return businessClient.get()
                .uri("/{id}", businessId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<BusinessResponse>>() {})
                .map(ApiResponse::getData);
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

    private Mono<Void> ensureOwnerAccessWhenApplicable(
            UserCredentialsResponse user,
            BusinessResponse business,
            List<StoreResponse> stores) {
        if (!java.util.Objects.equals(user.getId(), business.ownerUserId())) {
            return Mono.empty();
        }
        return userServiceClient.ensureOwnerBusinessAccess(
                user.getId(),
                business.id(),
                stores.stream().map(StoreResponse::id).toList());
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
