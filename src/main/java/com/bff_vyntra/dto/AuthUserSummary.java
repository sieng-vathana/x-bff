package com.bff_vyntra.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUserSummary {
    private Long id;
    private String username;
    private Set<String> permissions;
}
