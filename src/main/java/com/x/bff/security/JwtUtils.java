package com.x.bff.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
public class JwtUtils {

    private static final String PERMISSIONS_CLAIM = "permissions";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String CHANNEL_CLAIM = "ch";
    private static final String USER_ID_CLAIM = "uid";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final long expiration;
    private final long refreshExpiration;
    private final Key key;

    public JwtUtils(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration:1800000}") long expiration,
            @Value("${jwt.refresh-expiration:2592000000}") long refreshExpiration) {
        this.expiration = expiration;
        this.refreshExpiration = refreshExpiration;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username, Set<String> permissions) {
        return generateToken(username, permissions, null, null);
    }

    public String generateToken(
            String username,
            Set<String> permissions,
            String channel,
            Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(PERMISSIONS_CLAIM, permissions == null ? Collections.emptySet() : permissions);
        claims.put(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE);
        if (channel != null && !channel.isBlank()) {
            claims.put(CHANNEL_CLAIM, channel);
        }
        if (userId != null) {
            claims.put(USER_ID_CLAIM, userId);
        }
        return createToken(claims, username, expiration);
    }

    public String generateRefreshToken(String username) {
        return generateRefreshToken(username, null);
    }

    public String generateRefreshToken(String username, String channel) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE);
        if (channel != null && !channel.isBlank()) {
            claims.put(CHANNEL_CLAIM, channel);
        }
        return createToken(claims, username, refreshExpiration);
    }

    private String createToken(Map<String, Object> claims, String subject, Long expirationTime) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Boolean validateToken(String token, String username) {
        return validateToken(token, username, ACCESS_TOKEN_TYPE);
    }

    public Boolean validateRefreshToken(String token, String username) {
        return validateToken(token, username, REFRESH_TOKEN_TYPE);
    }

    private Boolean validateToken(String token, String username, String expectedType) {
        try {
            final String extractedUsername = extractUsername(token);
            final String tokenType = extractClaim(token, claims -> claims.get(TOKEN_TYPE_CLAIM, String.class));
            return extractedUsername.equals(username)
                    && expectedType.equals(tokenType)
                    && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public List<String> extractPermissions(String token) {
        Object value = extractClaim(token, claims -> claims.get(PERMISSIONS_CLAIM));
        if (!(value instanceof Collection<?> permissions)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (Object permission : permissions) {
            if (permission instanceof String permissionValue) {
                result.add(permissionValue);
            }
        }
        return result;
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /** Access-token TTL in seconds (for cookie Max-Age and client expiresIn). */
    public long getAccessExpirationSeconds() {
        return TimeUnit.MILLISECONDS.toSeconds(expiration);
    }

    /** Refresh-token TTL in seconds. */
    public long getRefreshExpirationSeconds() {
        return TimeUnit.MILLISECONDS.toSeconds(refreshExpiration);
    }
}
