package org.shpytchuk.adminapi.controller;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.adminapi.config.cache.CachedPage;
import org.shpytchuk.adminapi.form.ItemFilter;
import org.shpytchuk.adminapi.form.NotifyChannel;
import org.shpytchuk.adminapi.model.ItemModel;
import org.shpytchuk.adminapi.security.Action;
import org.shpytchuk.adminapi.security.RequirePermission;
import org.shpytchuk.adminapi.security.Scope;
import org.shpytchuk.adminapi.service.matching.MatchNotificationService;
import org.shpytchuk.adminapi.service.matching.MatchService;
import org.shpytchuk.adminapi.view.detail.ItemView;
import org.shpytchuk.adminapi.view.matching.CandidateView;
import org.shpytchuk.adminapi.view.matching.MatchRow;
import org.shpytchuk.adminapi.view.matching.NotifiedMatch;
import org.shpytchuk.adminapi.view.Pager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping(MatchController.BASE_PATH)
@AllArgsConstructor
@CachedPage
@Slf4j
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
        sources(model, matches.getContent());
        itemModel.forFilter(model, filter);
        return "matches";
    }

    @GetMapping("/{lostItemId}/candidates")
    @RequirePermission(scope = Scope.MATCH, action = Action.VIEW)
    public String candidates(@PathVariable Long lostItemId,
                             @RequestParam(defaultValue = "0") int page,
                             @ModelAttribute("filter") ItemFilter filter,
                             Model model) {
        MatchRow row = matchService.rowWithAllCandidates(lostItemId);
        model.addAttribute("row", row);
        model.addAttribute("page", page);
        sources(model, List.of(row));
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
        log.info("{} notified the owner of lost item {} about found item {} via {}",
                authentication.getName(), lostItemId, foundItemId, channel);

        model.addAttribute("lost", notified.lost());
        model.addAttribute("candidate", notified.candidate());
        itemModel.forSources(model, Scope.LOST_ITEM, List.of(notified.lost().id()));
        itemModel.forSources(model, Scope.FOUND_ITEM, List.of(notified.candidate().found().id()));
        return ROW;
    }

    private void sources(Model model, List<MatchRow> rows) {
        itemModel.forSources(model, Scope.LOST_ITEM, rows.stream().map(row -> row.lost().id()).toList());
        itemModel.forSources(model, Scope.FOUND_ITEM, rows.stream().flatMap(row -> row.candidates().stream())
                .map(CandidateView::found).map(ItemView::id).distinct().toList());
    }
}
