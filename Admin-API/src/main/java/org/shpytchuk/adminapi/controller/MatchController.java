package org.shpytchuk.adminapi.controller;

import lombok.AllArgsConstructor;
import org.shpytchuk.adminapi.config.cache.CachedPage;
import org.shpytchuk.adminapi.service.MatchNotificationService;
import org.shpytchuk.adminapi.service.MatchService;
import org.shpytchuk.adminapi.view.MatchRow;
import org.shpytchuk.adminapi.view.Pager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.shpytchuk.adminapi.security.Action;
import org.shpytchuk.adminapi.security.RequirePermission;
import org.shpytchuk.adminapi.security.Scope;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;

@Controller
@RequestMapping(MatchController.BASE_PATH)
@AllArgsConstructor
@CachedPage
public class MatchController {

    static final String BASE_PATH = "/admin/matches";

    private final MatchService matchService;
    private final MatchNotificationService notificationService;

    @GetMapping
    @RequirePermission(scope = Scope.MATCH, action = Action.VIEW)
    public String matches(Pageable pageable, Model model) {
        Page<MatchRow> matches = matchService.page(pageable);
        model.addAttribute("matches", matches);
        model.addAttribute("pages", Pager.of(matches));
        return "matches";
    }

    @PostMapping("/notify")
    @RequirePermission(scope = Scope.MATCH, action = Action.NOTIFY)
    public String notifyOwner(@RequestParam Long lostItemId,
                              @RequestParam Long foundItemId,
                              @RequestParam(defaultValue = "0") int page,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        Instant notifiedAt = notificationService.notifyOwner(lostItemId, foundItemId, authentication.getName());

        redirectAttributes.addFlashAttribute("message",
                "Сповіщення про знайдену річ #%d надіслано власнику загубленої #%d (%s)."
                        .formatted(foundItemId, lostItemId, notifiedAt));
        redirectAttributes.addAttribute("page", page);
        return "redirect:" + BASE_PATH;
    }
}
