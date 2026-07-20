package com.x.bff.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
