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
import com.x.bff.dto.TelegramMfaStatus;
import com.x.bff.dto.TelegramMfaChallenge;
import com.x.bff.dto.EmailMfaStatus;
import com.x.bff.dto.EmailMfaChallenge;
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

    private static final String MFA_TELEGRAM = "TELEGRAM";
    private static final String MFA_EMAIL = "EMAIL";
    private static final String MFA_CHOOSE = "CHOOSE";

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
                .flatMap(user -> requireActiveAccount(user).thenReturn(user))
                .flatMap(user -> enabledMfaMethods(user.getId()).flatMap(methods -> {
                    if (methods.isEmpty()) return createTokenResponseWithBusiness(user, channel);
                    String selectionToken = jwtUtils.generateMfaSelectionToken(user.getUsername());
                    // With more than one method the user picks where the code goes; nothing is sent yet.
                    if (methods.size() > 1) return Mono.just(chooseMethodResponse(methods, selectionToken, channel));
                    return startMfaChallenge(user, methods.get(0), methods, selectionToken, channel);
                }));
    }

    /** Sends a sign-in code to the chosen method once the password step has been passed. */
    public Mono<ApiResponse<AuthResponse>> sendMfaCode(
            String username, String selectionToken, String method, ClientChannel channel) {
        if (!Boolean.TRUE.equals(jwtUtils.validateMfaSelectionToken(selectionToken, username))) {
            return Mono.error(new BadCredentialsException("Your sign-in session expired. Please sign in again"));
        }
        return findUser(username)
                .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid sign-in verification")))
                .flatMap(user -> enabledMfaMethods(user.getId())
                        .filter(methods -> methods.contains(method))
                        .switchIfEmpty(Mono.error(new BadCredentialsException("This verification method is not turned on")))
                        .flatMap(methods -> startMfaChallenge(user, method, methods, selectionToken, channel)));
    }

    private Mono<List<String>> enabledMfaMethods(Long userId) {
        return Mono.zip(userServiceClient.telegramMfaStatus(userId), userServiceClient.emailMfaStatus(userId))
                .map(statuses -> {
                    List<String> methods = new java.util.ArrayList<>();
                    if (statuses.getT1().enabled()) methods.add(MFA_TELEGRAM);
                    if (statuses.getT2().enabled()) methods.add(MFA_EMAIL);
                    return List.copyOf(methods);
                });
    }

    private Mono<ApiResponse<AuthResponse>> startMfaChallenge(
            UserCredentialsResponse user, String method, List<String> methods, String selectionToken, ClientChannel channel) {
        Mono<ApiResponse<AuthResponse>> challenge = MFA_TELEGRAM.equals(method)
                ? userServiceClient.startTelegramMfaLogin(user.getId(), user.getUsername())
                        .map(sent -> telegramChallengeResponse(sent, channel))
                : userServiceClient.startEmailMfaLogin(user.getId())
                        .map(sent -> emailChallengeResponse(sent, channel));
        return challenge.doOnNext(response -> {
            response.getData().setMfaMethods(methods);
            response.getData().setMfaSelectionToken(selectionToken);
        });
    }

    private ApiResponse<AuthResponse> chooseMethodResponse(List<String> methods, String selectionToken, ClientChannel channel) {
        AuthResponse response = AuthResponse.builder()
                .mfaRequired(true)
                .mfaMethod(MFA_CHOOSE)
                .mfaMethods(methods)
                .mfaSelectionToken(selectionToken)
                .channel(channel.wireValue())
                .build();
        return ApiResponse.success(HttpStatus.OK.value(), response);
    }

    public Mono<ApiResponse<AuthResponse>> verifyTelegramMfa(
            String username, String challengeToken, String code, ClientChannel channel) {
        return findUser(username)
                .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid sign-in verification")))
                .flatMap(user -> userServiceClient.verifyTelegramMfa(user.getId(), challengeToken, code)
                        .filter(Boolean.TRUE::equals)
                        .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid or expired sign-in code")))
                        .then(createTokenResponseWithBusiness(user, channel)));
    }

    public Mono<ApiResponse<AuthResponse>> verifyEmailMfa(
            String username, String challengeToken, String code, ClientChannel channel) {
        return findUser(username)
                .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid sign-in verification")))
                .flatMap(user -> userServiceClient.verifyEmailMfa(user.getId(), challengeToken, code)
                        .filter(Boolean.TRUE::equals)
                        .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid or expired sign-in code")))
                        .then(createTokenResponseWithBusiness(user, channel)));
    }

    private ApiResponse<AuthResponse> telegramChallengeResponse(TelegramMfaChallenge challenge, ClientChannel channel) {
        AuthResponse response = AuthResponse.builder()
                .mfaRequired(true)
                .mfaMethod("TELEGRAM")
                .mfaChallengeToken(challenge.challengeToken())
                .mfaExpiresAt(challenge.expiresAt())
                .channel(channel.wireValue())
                .build();
        return ApiResponse.success(HttpStatus.OK.value(), response);
    }

    private ApiResponse<AuthResponse> emailChallengeResponse(EmailMfaChallenge challenge, ClientChannel channel) {
        AuthResponse response = AuthResponse.builder()
                .mfaRequired(true)
                .mfaMethod("EMAIL")
                .mfaChallengeToken(challenge.challengeToken())
                .mfaExpiresAt(challenge.expiresAt())
                .mfaDestination(challenge.maskedEmail())
                .channel(channel.wireValue())
                .build();
        return ApiResponse.success(HttpStatus.OK.value(), response);
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
                    .doOnNext(business -> created.business = business)
                    .flatMap(business -> createStore(business.id(), request))
                    .doOnNext(store -> created.storeId = store.id())
                    .flatMap(store -> createMarketplaceCustomer(created.userId, created.businessId, store.id(), request)
                            .then(userServiceClient.ensureOwnerBusinessAccess(
                                    created.userId, created.businessId, List.of(store.id())))
                            .thenReturn(store))
                    .switchIfEmpty(Mono.error(() -> new IllegalStateException("Registration did not complete")))
                    .onErrorResume(error -> undoRegistration(request.username(), created).then(Mono.error(error)))
                    .flatMap(store -> applicationReceived(created, request, store, channel));
        });
    }

    /**
     * A new merchant does not get a session: the business waits for TinhLuk approval. The owner is emailed that the
     * application arrived, and again when it is approved or needs changes.
     */
    private Mono<ApiResponse<AuthResponse>> applicationReceived(
            CreatedRecords created, RegistrationRequest request, StoreResponse store, ClientChannel channel) {
        String businessName = created.business == null ? request.businessName() : created.business.name();
        return userServiceClient.sendNotice(created.userId,
                        "We received your merchant application",
                        "Application received",
                        "Thank you for applying to sell on TinhLuk.\n\n"
                                + "Business: " + businessName + "\nShop: " + store.name() + "\n\n"
                                + "Our team is reviewing your application. We will email you as soon as it is approved, "
                                + "usually within one business day. You can sign in to the merchant portal once it is approved.")
                .thenReturn(ApiResponse.success(HttpStatus.ACCEPTED.value(), "Application submitted for review",
                        AuthResponse.builder()
                                .applicationStatus("PENDING_REVIEW")
                                .business(created.business)
                                .stores(List.of(store))
                                .channel(channel.wireValue())
                                .build()));
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
                        request.timeZone(), request.fiscalYearStartMonth(), request.usdToKhrExchangeRate(),
                        request.plan(), request.billingCycle()))
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
            String timeZone, Integer fiscalYearStartMonth, java.math.BigDecimal usdToKhrExchangeRate,
            String plan, String billingCycle) {
    }

    private record MarketplaceCustomerCommand(
            Long userId, Long businessId, Long storeId, String fullName, String phone, String email) {
    }

    /** What a registration has created so far, so a later failure can remove it again. */
    private static final class CreatedRecords {
        private Long userId;
        private Long businessId;
        private Long storeId;
        private BusinessResponse business;
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
                                .flatMap(scopedUser -> refuseSuspended(scopedUser, business, stores).thenReturn(scopedUser))
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

    /**
     * Checked right after the password, before any 2FA code is sent, so a suspended account learns why at once.
     * Sign-in, token refresh and 2FA verification check again when the session is issued.
     */
    private Mono<Void> requireActiveAccount(UserCredentialsResponse user) {
        return findPrimaryBusiness(user)
                .flatMap(business -> findStores(business.id())
                        .flatMap(stores -> userServiceClient.findByUsername(user.getUsername(), business.id())
                                .flatMap(scopedUser -> refuseSuspended(scopedUser, business, stores))))
                .then();
    }

    /**
     * A suspended merchant cannot sign in at all. Staff whose every store is suspended cannot either; the owner
     * still can, to see which store is suspended and why.
     */
    private Mono<Void> refuseSuspended(UserCredentialsResponse user, BusinessResponse business, List<StoreResponse> stores) {
        if (business.status() != null && business.status() == 0) {
            return Mono.error(new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your merchant application for " + business.name() + " is under review. "
                            + "We will email you as soon as TinhLuk approves it."));
        }
        if (business.status() != null && business.status() == -1) {
            return Mono.error(new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your merchant application for " + business.name() + " was not approved by TinhLuk"
                            + reasonSuffix(business.reviewReason()) + " Contact TinhLuk support for help."));
        }
        if (business.suspended()) {
            return Mono.error(new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your merchant account is suspended by TinhLuk" + reasonSuffix(business.suspensionReason())
                            + " Contact TinhLuk support for help."));
        }
        if (java.util.Objects.equals(business.ownerUserId(), user.getId())) return Mono.empty();
        List<StoreResponse> ownStores = stores.stream()
                .filter(store -> user.getStoreIds() != null && user.getStoreIds().contains(store.id()))
                .toList();
        if (!ownStores.isEmpty() && ownStores.stream().allMatch(StoreResponse::suspended)) {
            StoreResponse store = ownStores.get(0);
            return Mono.error(new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
                    (ownStores.size() == 1 ? "Your store " + store.name() + " is" : "Your stores are")
                            + " suspended by TinhLuk" + reasonSuffix(store.suspensionReason())
                            + " Ask the business owner or TinhLuk support for help."));
        }
        return Mono.empty();
    }

    private static String reasonSuffix(String reason) {
        return reason == null || reason.isBlank() ? "." : ": " + reason.trim() + ".";
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
                        user.getUsername(), permissions, channelValue, user.getId(), business == null ? null : business.id()))
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
