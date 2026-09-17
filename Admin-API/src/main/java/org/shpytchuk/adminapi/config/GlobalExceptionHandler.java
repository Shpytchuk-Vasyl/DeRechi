package org.shpytchuk.adminapi.config;

import org.shpytchuk.adminapi.exception.NotFoundException;
import org.shpytchuk.adminapi.security.AdminPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(NotFoundException exception, Model model) {
        describeAdmin(model);
        model.addAttribute("message", exception.getMessage());
        return "error/404";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String accessDenied(AccessDeniedException exception, Model model) {
        log.debug("Відмова в доступі", exception);
        describeAdmin(model);
        model.addAttribute("message", "Для цієї дії бракує прав.");
        return "error/403";
    }

    private static void describeAdmin(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("perms", AdminPermissions.of(authentication));
        model.addAttribute("currentUser", authentication == null ? null : authentication.getName());
    }
}
