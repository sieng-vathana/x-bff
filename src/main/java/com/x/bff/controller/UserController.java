package com.x.bff.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.x.bff.service.ServiceClientFactory;
import com.x.bff.utils.XUtil;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final WebClient userClient;

    public UserController(ServiceClientFactory clientFactory) {
        this.userClient = clientFactory.forService("user", "");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('x-user:read') or hasAuthority('x-user:create') or hasAuthority('x-store:read')")
    public Mono<ResponseEntity<?>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return forward(userClient.get().uri(uri -> uri
                .queryParam("page", page)
                .queryParam("size", size)
                .build()));
    }

    @GetMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAuthority('x-user:read') or hasAuthority('x-user:create')")
    public Mono<ResponseEntity<?>> getUserById(@PathVariable Long id) {
        return forward(userClient.get().uri(uri -> uri.path("/by-id/{id}").build(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('x-user:create') or hasAuthority('x-user:read')")
    public Mono<ResponseEntity<?>> createUser(@RequestBody JsonNode request) {
        return forward(userClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request));
    }

    @PutMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAuthority('x-user:update') or hasAuthority('x-user:read')")
    public Mono<ResponseEntity<?>> updateUser(
            @PathVariable Long id,
            @RequestBody JsonNode request) {
        return forward(userClient.put().uri(uri -> uri.path("/{id}").build(id))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request));
    }

    @DeleteMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAuthority('x-user:delete') or hasAuthority('x-user:read')")
    public Mono<ResponseEntity<?>> deleteUser(@PathVariable Long id) {
        return forward(userClient.delete().uri(uri -> uri.path("/{id}").build(id)));
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('x-user:read') or hasAuthority('x-user:create')")
    public Mono<ResponseEntity<?>> getRoles() {
        return forward(userClient.get().uri("/roles"));
    }

    @GetMapping("/roles/{id:[0-9]+}")
    @PreAuthorize("hasAuthority('x-user:read') or hasAuthority('x-user:create')")
    public Mono<ResponseEntity<?>> getRoleDetails(@PathVariable Long id) {
        return forward(userClient.get().uri(uri -> uri.path("/roles/{id}").build(id)));
    }

    private Mono<ResponseEntity<?>> forward(WebClient.RequestHeadersSpec<?> request) {
        return request.exchangeToMono(response -> response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> XUtil.toJsonResponse(body, response.statusCode())));
    }
}
