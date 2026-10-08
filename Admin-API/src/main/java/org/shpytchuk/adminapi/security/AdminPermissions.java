package org.shpytchuk.adminapi.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public final class AdminPermissions {

    public static final AdminPermissions NONE = new AdminPermissions(Set.of());

    private final Set<String> authorities;

    private AdminPermissions(Set<String> authorities) {
        this.authorities = authorities;
    }

    public static AdminPermissions of(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return NONE;
        }
        return of(authentication.getAuthorities());
    }

    public static AdminPermissions of(Collection<? extends GrantedAuthority> granted) {
        return new AdminPermissions(granted.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet()));
    }

    public boolean can(String scope, String action) {
        return can(Scope.valueOf(scope), Action.valueOf(action));
    }

    public boolean can(Scope scope, Action action) {
        return scope.supports(action) && authorities.contains(Permissions.authority(scope, action));
    }

    public Set<String> authorities() {
        return authorities;
    }
}
