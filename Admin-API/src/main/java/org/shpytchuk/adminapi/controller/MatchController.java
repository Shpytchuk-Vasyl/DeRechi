package org.shpytchuk.adminapi.controller;

import lombok.AllArgsConstructor;
import org.shpytchuk.adminapi.config.cache.CachedPage;
import org.shpytchuk.adminapi.form.ItemFilter;
import org.shpytchuk.adminapi.security.Action;
import org.shpytchuk.adminapi.security.RequirePermission;
import org.shpytchuk.adminapi.security.Scope;
import org.shpytchuk.adminapi.service.MatchNotificationService;
import org.shpytchuk.adminapi.service.MatchService;
import org.shpytchuk.adminapi.view.MatchRow;
import org.shpytchuk.adminapi.view.Pager;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
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
    private final ItemModel itemModel;
    private final MessageSource messages;

    @GetMapping
    @RequirePermission(scope = Scope.MATCH, action = Action.VIEW)
    public String matches(Pageable pageable,
                          @ModelAttribute("filter") ItemFilter filter,
                          Model model) {
        Page<MatchRow> matches = matchService.page(pageable, filter);
        model.addAttribute("matches", matches);
        model.addAttribute("pages", Pager.of(matches));
        model.addAttribute("titleKey", "page.matches");
        itemModel.forFilter(model, filter);
        return "matches";
    }

    @GetMapping("/{lostItemId}/candidates")
    @RequirePermission(scope = Scope.MATCH, action = Action.VIEW)
    public String candidates(@PathVariable Long lostItemId,
                             @RequestParam(defaultValue = "0") int page,
                             @ModelAttribute("filter") ItemFilter filter,
                             Model model) {
        model.addAttribute("row", matchService.rowWithAllCandidates(lostItemId));
        model.addAttribute("page", page);
        return "fragments/candidates :: cell(row=${row}, page=${page}, filter=${filter})";
    }

    @PostMapping("/notify")
    @RequirePermission(scope = Scope.MATCH, action = Action.NOTIFY)
    public String notifyOwner(@RequestParam Long lostItemId,
                              @RequestParam Long foundItemId,
                              @RequestParam(defaultValue = "0") int page,
                              @ModelAttribute("filter") ItemFilter filter,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        Instant notifiedAt = notificationService.notifyOwner(lostItemId, foundItemId, authentication.getName());

        redirectAttributes.addFlashAttribute("message", messages.getMessage("flash.match.notified",
                new Object[]{foundItemId, lostItemId, notifiedAt}, LocaleContextHolder.getLocale()));
        redirectAttributes.addAttribute("page", page);
        addFilter(redirectAttributes, filter);
        return "redirect:" + BASE_PATH;
    }

    private static void addFilter(RedirectAttributes redirectAttributes, ItemFilter filter) {
        if (filter.q() != null) {
            redirectAttributes.addAttribute("q", filter.q());
        }
        if (filter.categoryId() != null) {
            redirectAttributes.addAttribute("categoryId", filter.categoryId());
        }
        if (filter.from() != null) {
            redirectAttributes.addAttribute("from", filter.from());
        }
        if (filter.to() != null) {
            redirectAttributes.addAttribute("to", filter.to());
        }
    }
}
