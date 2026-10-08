package org.shpytchuk.adminapi.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

public class LoginAudit {

    private static final Logger log = LoggerFactory.getLogger(LoginAudit.class);

    @EventListener
    public void loggedIn(InteractiveAuthenticationSuccessEvent event) {
        log.info("{} logged in", event.getAuthentication().getName());
    }

    @EventListener
    public void loggedOut(LogoutSuccessEvent event) {
        log.info("{} logged out", event.getAuthentication().getName());
    }

    public AuthenticationFailureHandler failureHandler(AuthenticationFailureHandler delegate) {
        return (request, response, exception) -> {
            log.warn("Login failed: {}", getMostSpecificCause(exception).toString());
            delegate.onAuthenticationFailure(request, response, exception);
        };
    }
}
