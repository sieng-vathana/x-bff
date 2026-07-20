package com.x.bff.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCredentialsResponse {
    private Long id;
    private String username;
    private String password;
    private Set<String> permissions;
}
