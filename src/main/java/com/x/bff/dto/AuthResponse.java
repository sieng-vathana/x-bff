package com.x.bff.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {
    /**
     * Present for mobile clients. Omitted (null) for web when tokens are cookie-only.
     */
    private String accessToken;
    /**
     * Present for mobile clients. Omitted (null) for web when tokens are cookie-only.
     */
    private String refreshToken;
    /** Access token lifetime in seconds. */
    private Long expiresIn;
    /** Always {@code Bearer} for access tokens. */
    private String tokenType;
    /** {@code web} or {@code mobile}. */
    private String channel;
    private AuthUserSummary user;
    /** The signed-in user's primary business, included to hydrate the web client in one response. */
    private BusinessResponse business;
    /** Stores for the primary business, so the initial workspace needs no follow-up request. */
    private List<StoreResponse> stores;
    /** Set when the password was accepted but a second factor is still required. */
    private Boolean mfaRequired;
    private String mfaMethod;
    private String mfaChallengeToken;
    private java.time.Instant mfaExpiresAt;
    private String mfaDestination;
    /** Second-factor methods the user has turned on, e.g. TELEGRAM and EMAIL. */
    private List<String> mfaMethods;
    /** Short-lived proof that the password was accepted, used to send a code to a chosen method. */
    private String mfaSelectionToken;
    /** PENDING_REVIEW after registering: the application waits for TinhLuk approval and no session is issued. */
    private String applicationStatus;
}
