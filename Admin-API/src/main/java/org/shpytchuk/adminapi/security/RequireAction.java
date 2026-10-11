package org.shpytchuk.adminapi.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @RequireAction(Action.EDIT)} on a handler of a controller that exposes {@code public Scope scope()}
 * (today {@code ItemController}): the action is fixed by the method, the scope is read from the controller
 * instance at request time, so handlers shared through a base class are guarded once and still require the
 * {@code SCOPE:ACTION} of the concrete controller.
 * <p>
 * Same mechanism as {@link RequirePermission}: a meta-annotation over {@code @PreAuthorize} whose
 * {@code {value}} placeholder is filled by {@code AnnotationTemplateExpressionDefaults}; {@code this} is the
 * target object Spring Security puts on the expression root. A handler with a constant scope keeps using
 * {@code @RequirePermission}. The contract is not an interface on purpose: an interface would let method
 * security wrap the controller in a JDK proxy, which is no longer a {@code @Controller}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("@perm.can(authentication, this.scope().name(), '{value}')")
public @interface RequireAction {

    Action value();
}
