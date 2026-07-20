package com.x.bff.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mobile clients send the refresh token in the JSON body.
 * Web clients may omit the body and rely on the HttpOnly refresh cookie.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRequest {
    private String refreshToken;
}
