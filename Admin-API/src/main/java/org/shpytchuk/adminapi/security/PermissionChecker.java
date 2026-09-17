package org.shpytchuk.adminapi.security;

import org.springframework.security.core.Authentication;

import java.util.List;

/**
 * {@code @PreAuthorize("@perm.can(authentication, 'LOST_ITEM', 'EDIT')")}.
 */
public class PermissionChecker {

    private static final List<Scope> ITEM_SCOPES = List.of(
            Scope.LOST_ITEM, Scope.FOUND_ITEM, Scope.LOST_ITEM_HISTORY, Scope.FOUND_ITEM_HISTORY);

    public boolean can(Authentication authentication, String scope, String action) {
        return AdminPermissions.of(authentication).can(scope, action);
    }

    public boolean canManageItems(Authentication authentication) {
        AdminPermissions permissions = AdminPermissions.of(authentication);

        return ITEM_SCOPES.stream().anyMatch(scope ->
                permissions.can(scope, Action.CREATE) || permissions.can(scope, Action.EDIT));
    }
}
