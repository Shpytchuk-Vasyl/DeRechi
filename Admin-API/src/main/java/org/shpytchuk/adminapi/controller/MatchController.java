package org.shpytchuk.adminapi.controller;

import lombok.AllArgsConstructor;
import org.shpytchuk.adminapi.config.cache.CachedPage;
import org.shpytchuk.adminapi.form.ItemFilter;
import org.shpytchuk.adminapi.form.NotifyChannel;
import org.shpytchuk.adminapi.model.ItemModel;
import org.shpytchuk.adminapi.security.Action;
import org.shpytchuk.adminapi.security.RequirePermission;
import org.shpytchuk.adminapi.security.Scope;
import org.shpytchuk.adminapi.service.matching.MatchNotificationService;
import org.shpytchuk.adminapi.service.matching.MatchService;
import org.shpytchuk.adminapi.view.matching.MatchRow;
import org.shpytchuk.adminapi.view.matching.NotifiedMatch;
import org.shpytchuk.adminapi.view.Pager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping(MatchController.BASE_PATH)
@AllArgsConstructor
@CachedPage
public class MatchController {

    static final String BASE_PATH = "/admin/matches";

    private static final String CELL = "fragments/candidates :: cell(row=${row}, page=${page}, filter=${filter})";
    private static final String ROW = "fragments/candidates :: row(lost=${lost}, candidate=${candidate})";

    private final MatchService matchService;
    private final MatchNotificationService notificationService;
    private final ItemModel itemModel;

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
        return CELL;
    }

    @PostMapping("/notify")
    @RequirePermission(scope = Scope.MATCH, action = Action.NOTIFY)
    public String notifyOwner(@RequestParam Long lostItemId,
                              @RequestParam Long foundItemId,
                              @RequestParam(defaultValue = "ALL") NotifyChannel channel,
                              Authentication authentication,
                              Model model) {
        NotifiedMatch notified = notificationService.notifyOwner(
                lostItemId, foundItemId, authentication.getName(), channel);

        model.addAttribute("lost", notified.lost());
        model.addAttribute("candidate", notified.candidate());
        return ROW;
    }
}
