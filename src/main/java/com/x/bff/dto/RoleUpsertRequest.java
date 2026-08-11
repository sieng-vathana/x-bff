package com.x.bff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record RoleUpsertRequest(
        @NotNull @Positive Long businessId,
        @NotBlank @Size(max = 120) String roleName,
        @Size(max = 64) String roleCode,
        @Size(max = 500) String description,
        @NotNull Set<@Positive Long> permissionIds) {
}
