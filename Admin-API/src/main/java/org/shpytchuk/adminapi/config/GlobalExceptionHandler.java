package org.shpytchuk.adminapi.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.shpytchuk.adminapi.exception.NotFoundException;
import org.shpytchuk.adminapi.security.AdminPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.ui.Model;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messages;

    public GlobalExceptionHandler(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(NotFoundException exception, Model model) {
        describeAdmin(model);
        model.addAttribute("message", text("error.notFound",
                text(exception.getEntityKey()), exception.getId()));
        return "error/404";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String accessDenied(AccessDeniedException exception, Model model, HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.warn("{} was denied {} {}", authentication == null ? null : authentication.getName(),
                request.getMethod(), request.getRequestURI());
        describeAdmin(model);
        model.addAttribute("message", text("error.forbidden"));
        return "error/403";
    }

    @ExceptionHandler(Exception.class)
    public String unexpected(Exception exception, Model model,
                             HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (exception instanceof ErrorResponse) {
            throw exception;
        }

        if (isTimeout(exception)) {
            log.warn("{} {} timed out: {}", request.getMethod(), request.getRequestURI(),
                    getMostSpecificCause(exception).toString());
            response.setStatus(HttpStatus.REQUEST_TIMEOUT.value());
            describeAdmin(model);
            model.addAttribute("message", text("error.timeout"));
            return "error/408";
        }

        log.error("{} {} failed", request.getMethod(), request.getRequestURI(), exception);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        describeAdmin(model);
        model.addAttribute("message", text("error.unexpected"));
        return "error/500";
    }

    private static boolean isTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof QueryTimeoutException
                    || cause instanceof jakarta.persistence.QueryTimeoutException
                    || cause instanceof TransactionTimedOutException) {
                return true;
            }
            if (cause instanceof org.hibernate.TransactionException
                    && cause.getMessage() != null
                    && cause.getMessage().toLowerCase().contains("timeout")) {
                return true;
            }
        }
        return false;
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    private static void describeAdmin(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("perms", AdminPermissions.of(authentication));
        model.addAttribute("languages", LocaleConfig.SUPPORTED);
        model.addAttribute("currentLanguage", LocaleConfig.current());
        model.addAttribute("currentUser", authentication == null ? null : authentication.getName());
    }
}
