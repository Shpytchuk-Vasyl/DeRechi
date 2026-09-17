package org.shpytchuk.adminapi.controller;

import jakarta.validation.Valid;
import org.shpytchuk.adminapi.entity.items.LostItemHistory;
import org.shpytchuk.adminapi.form.ItemFilter;
import org.shpytchuk.adminapi.form.ItemForm;
import org.shpytchuk.adminapi.security.Action;
import org.shpytchuk.adminapi.security.RequirePermission;
import org.shpytchuk.adminapi.security.Scope;
import org.shpytchuk.adminapi.service.LostItemHistoryAdminService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping(LostItemHistoryController.BASE_PATH)
public class LostItemHistoryController extends ItemController<LostItemHistory> {

    static final String BASE_PATH = "/admin/lost-items-history";
    private static final String TITLE = "Архів загублених речей";

    public LostItemHistoryController(LostItemHistoryAdminService service, ItemModel itemModel) {
        super(service, itemModel, Scope.LOST_ITEM_HISTORY, BASE_PATH, TITLE);
    }

    @Override
    @GetMapping
    @RequirePermission(scope = Scope.LOST_ITEM_HISTORY, action = Action.VIEW)
    public String list(Pageable pageable,
                       @ModelAttribute("filter") ItemFilter filter,
                       Model model) {
        return super.list(pageable, filter, model);
    }

    @Override
    @GetMapping("/new")
    @RequirePermission(scope = Scope.LOST_ITEM_HISTORY, action = Action.CREATE)
    public String createForm(Model model) {
        return super.createForm(model);
    }

    @Override
    @PostMapping
    @RequirePermission(scope = Scope.LOST_ITEM_HISTORY, action = Action.CREATE)
    public String create(@Valid @ModelAttribute("form") ItemForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        return super.create(form, binding, model, redirectAttributes);
    }

    @Override
    @GetMapping("/{id}/edit")
    @RequirePermission(scope = Scope.LOST_ITEM_HISTORY, action = Action.EDIT)
    public String editForm(@PathVariable Long id, Model model) {
        return super.editForm(id, model);
    }

    @Override
    @PostMapping("/{id}")
    @RequirePermission(scope = Scope.LOST_ITEM_HISTORY, action = Action.EDIT)
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") ItemForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        return super.update(id, form, binding, model, redirectAttributes);
    }

    @Override
    @PostMapping("/{id}/delete")
    @RequirePermission(scope = Scope.LOST_ITEM_HISTORY, action = Action.DELETE)
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return super.delete(id, redirectAttributes);
    }
}
