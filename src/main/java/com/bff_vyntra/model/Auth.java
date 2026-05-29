package com.bff_vyntra.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Auth {

    private Long id;
    private String username;
    private String password;
    private Set<String> permissions;
}
