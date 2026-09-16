package org.shpytchuk.adminapi.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @RequirePermission(scope = Scope.LOST_ITEM, action = Action.EDIT)}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@PreAuthorize("@perm.can(authentication, '{scope}', '{action}')")
public @interface RequirePermission {

    Scope scope();

    Action action();
}
