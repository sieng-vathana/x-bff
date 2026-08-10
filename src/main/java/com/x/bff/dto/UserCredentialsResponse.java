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
    private String fullName;
    private String email;
    private String password;
    private Set<String> permissions;

    /** Keeps the authentication service's existing constructor contract. */
    public UserCredentialsResponse(Long id, String username, String password, Set<String> permissions) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.permissions = permissions;
    }
}
