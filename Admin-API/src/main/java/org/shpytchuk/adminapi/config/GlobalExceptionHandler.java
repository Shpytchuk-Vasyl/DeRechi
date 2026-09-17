package org.shpytchuk.adminapi.config;

import jakarta.servlet.http.HttpServletResponse;
import org.shpytchuk.adminapi.exception.NotFoundException;
import org.shpytchuk.adminapi.security.AdminPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.ErrorResponse;
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

    @ExceptionHandler(Exception.class)
    public String unexpected(Exception exception, Model model, HttpServletResponse response) throws Exception {
        if (exception instanceof ErrorResponse) {
            throw exception;
        }

        if (isTimeout(exception)) {
            log.warn("Таймаут при обробці запиту", exception);
            response.setStatus(HttpStatus.REQUEST_TIMEOUT.value());
            describeAdmin(model);
            model.addAttribute("message", "Запит виконувався надто довго і був перерваний.");
            return "error/408";
        }

        log.error("Необроблена помилка", exception);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        describeAdmin(model);
        model.addAttribute("message", "Щось пішло не так. Спробуй ще раз або звернись до розробників.");
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

    private static void describeAdmin(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("perms", AdminPermissions.of(authentication));
        model.addAttribute("currentUser", authentication == null ? null : authentication.getName());
    }
}
