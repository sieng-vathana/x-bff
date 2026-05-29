package com.bff_vyntra.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

// @Configuration
// @EnableWebFluxSecurity
// @EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtils jwtUtils;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
                        .anyExchange().permitAll()
                )
                .addFilterAt(authenticationWebFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    private AuthenticationWebFilter authenticationWebFilter() {
        AuthenticationWebFilter filter = new AuthenticationWebFilter(authenticationManager());
        filter.setServerAuthenticationConverter(authenticationConverter());
        return filter;
    }

    private ServerAuthenticationConverter authenticationConverter() {
        return exchange -> {
            String token = null;
            String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            } else {
                org.springframework.http.HttpCookie cookie = exchange.getRequest().getCookies().getFirst("access_token");
                if (cookie != null) {
                    token = cookie.getValue();
                }
            }

            if (token != null) {
                try {
                    String username = jwtUtils.extractUsername(token);
                    List<String> permissions = jwtUtils.extractPermissions(token);
                    List<SimpleGrantedAuthority> authorities = permissions != null ?
                            permissions.stream()
                                    .map(p -> new SimpleGrantedAuthority("PERMISSION_" + p))
                                    .collect(Collectors.toList()) : Collections.emptyList();

                    return Mono.just(new UsernamePasswordAuthenticationToken(username, token, authorities));
                } catch (Exception e) {
                    return Mono.empty();
                }
            }
            return Mono.empty();
        };
    }

    private ReactiveAuthenticationManager authenticationManager() {
        return authentication -> {
            String token = (String) authentication.getCredentials();
            String username = authentication.getName();
            if (jwtUtils.validateToken(token, username)) {
                return Mono.just(authentication);
            }
            return Mono.empty();
        };
    }
}
