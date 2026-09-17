package org.shpytchuk.adminapi.controller;

import lombok.AllArgsConstructor;
import org.shpytchuk.adminapi.config.LocaleConfig;
import org.springframework.context.i18n.LocaleContextHolder;
import org.shpytchuk.adminapi.security.AdminPermissions;
import org.shpytchuk.adminapi.view.Formats;
import org.shpytchuk.adminapi.view.Plurals;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;
import java.util.Locale;

@ControllerAdvice
@AllArgsConstructor
public class GlobalModelAdvice {

    private final Formats formats;
    private final Plurals plurals;

    @ModelAttribute("fmt")
    public Formats formats() {
        return formats;
    }

    @ModelAttribute("plural")
    public Plurals plurals() {
        return plurals;
    }

    @ModelAttribute("languages")
    public List<Locale> languages() {
        return LocaleConfig.SUPPORTED;
    }

    /**
     * Резолвер віддає локаль без країни (з {@code ?lang=en} виходить просто "en"),
     * а прапор будується саме з коду країни — тому беремо відповідник зі списку.
     */
    @ModelAttribute("currentLanguage")
    public Locale currentLanguage() {
        String language = LocaleContextHolder.getLocale().getLanguage();
        return LocaleConfig.SUPPORTED.stream()
                .filter(supported -> supported.getLanguage().equals(language))
                .findFirst()
                .orElse(LocaleConfig.SUPPORTED.getFirst());
    }

    @ModelAttribute("perms")
    public AdminPermissions permissions(Authentication authentication) {
        return AdminPermissions.of(authentication);
    }

    @ModelAttribute("currentUser")
    public String currentUser(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
}
