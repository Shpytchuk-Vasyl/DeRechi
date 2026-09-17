package org.shpytchuk.adminapi.controller;

import lombok.AllArgsConstructor;
import org.shpytchuk.adminapi.config.LocaleConfig;
import org.shpytchuk.adminapi.security.AdminPermissions;
import org.shpytchuk.adminapi.view.Formats;
import org.shpytchuk.adminapi.view.Plurals;
import org.shpytchuk.adminapi.view.SocialMediaIcons;
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
    private final SocialMediaIcons socialMediaIcons;

    @ModelAttribute("fmt")
    public Formats formats() {
        return formats;
    }

    @ModelAttribute("plural")
    public Plurals plurals() {
        return plurals;
    }

    @ModelAttribute("social")
    public SocialMediaIcons socialMediaIcons() {
        return socialMediaIcons;
    }

    @ModelAttribute("languages")
    public List<Locale> languages() {
        return LocaleConfig.SUPPORTED;
    }

    @ModelAttribute("currentLanguage")
    public Locale currentLanguage() {
        return LocaleConfig.current();
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
