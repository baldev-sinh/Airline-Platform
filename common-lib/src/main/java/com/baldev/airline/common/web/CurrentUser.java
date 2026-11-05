package com.baldev.airline.common.web;

import com.baldev.airline.common.enums.UserRole;

import java.util.Set;

/**
 * The authenticated caller, resolved from gateway identity headers. Declare it as a controller method
 * parameter to receive it; requests without identity headers are rejected with 401.
 */
public record CurrentUser(Long id, String email, Set<UserRole> roles) {

    public CurrentUser {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean hasRole(UserRole role) {
        return roles.contains(role);
    }

    public boolean isSystemAdmin() {
        return hasRole(UserRole.ROLE_SYSTEM_ADMIN);
    }
}
