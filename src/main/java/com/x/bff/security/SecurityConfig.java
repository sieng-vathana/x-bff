package com.x.bff.security;

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

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtils jwtUtils;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((exchange, exception) -> {
                    exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/marketplace/home")
                        .permitAll()
                        .anyExchange().authenticated()
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
                    // Authorities = JWT permission codes as-is: x-{service}:{action}
                    // e.g. x-product:read, x-order:create, x-inventory:stock-in
                    List<SimpleGrantedAuthority> authorities = permissions != null
                            ? permissions.stream()
                            .filter(p -> p != null && !p.isBlank())
                            .map(SimpleGrantedAuthority::new)
                            .distinct()
                            .collect(Collectors.toList())
                            : Collections.emptyList();

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
