package org.shpytchuk.adminapi.security;

import java.util.LinkedHashSet;
import java.util.Set;

public final class Permissions {

    private Permissions() {
    }

    public static String authority(Scope scope, Action action) {
        if (!scope.supports(action)) {
            throw new IllegalArgumentException("Action " + action + " not supported for " + scope);
        }
        return scope.name() + ":" + action.name();
    }

    public static Set<String> all() {
        Set<String> authorities = new LinkedHashSet<>();
        for (Scope scope : Scope.values()) {
            for (Action action : Action.values()) {
                if (scope.supports(action)) {
                    authorities.add(authority(scope, action));
                }
            }
        }
        return authorities;
    }
}
