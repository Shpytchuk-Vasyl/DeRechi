package org.shpytchuk.adminapi.controller;

import org.shpytchuk.adminapi.security.AdminPermissions;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAdvice {

    @ModelAttribute("perms")
    public AdminPermissions permissions(Authentication authentication) {
        return AdminPermissions.of(authentication);
    }

    @ModelAttribute("currentUser")
    public String currentUser(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
}
