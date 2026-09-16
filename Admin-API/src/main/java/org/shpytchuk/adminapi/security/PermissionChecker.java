package org.shpytchuk.adminapi.security;

import org.springframework.security.core.Authentication;

/**
 * {@code @PreAuthorize("@perm.can(authentication, 'LOST_ITEM', 'EDIT')")}.
 */
public class PermissionChecker {

    public boolean can(Authentication authentication, String scope, String action) {
        return AdminPermissions.of(authentication).can(scope, action);
    }
}
