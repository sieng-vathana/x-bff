package com.x.bff.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class RoleControllerSecurityTest {

    @Test
    void roleEndpointsRequireTheMatchingUserPermission() throws NoSuchMethodException {
        assertPermission("list", "hasAuthority('x-user:read')", Long.class,
                org.springframework.security.core.Authentication.class);
        assertPermission("create", "hasAuthority('x-user:create')",
                com.x.bff.dto.RoleUpsertRequest.class,
                org.springframework.security.core.Authentication.class);
        assertPermission("update", "hasAuthority('x-user:update')", Long.class,
                com.x.bff.dto.RoleUpsertRequest.class,
                org.springframework.security.core.Authentication.class);
        assertPermission("delete", "hasAuthority('x-user:delete')", Long.class, Long.class,
                org.springframework.security.core.Authentication.class);
    }

    private void assertPermission(String method, String expected, Class<?>... parameters)
            throws NoSuchMethodException {
        PreAuthorize annotation = RoleController.class.getMethod(method, parameters)
                .getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }
}
