package org.shpytchuk.adminapi.controller;

import jakarta.validation.Valid;
import org.shpytchuk.adminapi.entity.lost.LostItem;
import org.shpytchuk.adminapi.form.ItemFilter;
import org.shpytchuk.adminapi.form.ItemForm;
import org.shpytchuk.adminapi.form.ItemFormValidator;
import org.shpytchuk.adminapi.model.ItemModel;
import org.shpytchuk.adminapi.security.Action;
import org.shpytchuk.adminapi.security.RequirePermission;
import org.shpytchuk.adminapi.security.Scope;
import org.shpytchuk.adminapi.service.lost.LostItemAdminService;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping(LostItemController.BASE_PATH)
public class LostItemController extends ItemController<LostItem> {

    static final String BASE_PATH = "/admin/lost-items";
    private static final String TITLE_KEY = "page.lost_item";

    public LostItemController(LostItemAdminService service, ItemModel itemModel,
                               ItemFormValidator formValidator, MessageSource messages) {
        super(service, itemModel, formValidator, messages, Scope.LOST_ITEM, BASE_PATH, TITLE_KEY);
    }

    @Override
    @GetMapping
    @RequirePermission(scope = Scope.LOST_ITEM, action = Action.VIEW)
    public String list(Pageable pageable,
                       @ModelAttribute("filter") ItemFilter filter,
                       Model model) {
        return super.list(pageable, filter, model);
    }

    @Override
    @GetMapping("/new")
    @RequirePermission(scope = Scope.LOST_ITEM, action = Action.CREATE)
    public String createForm(Model model) {
        return super.createForm(model);
    }

    @Override
    @PostMapping
    @RequirePermission(scope = Scope.LOST_ITEM, action = Action.CREATE)
    public String create(@Valid @ModelAttribute("form") ItemForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        return super.create(form, binding, model, redirectAttributes);
    }

    @Override
    @GetMapping("/{id}/edit")
    @RequirePermission(scope = Scope.LOST_ITEM, action = Action.EDIT)
    public String editForm(@PathVariable Long id, Model model) {
        return super.editForm(id, model);
    }

    @Override
    @PostMapping("/{id}")
    @RequirePermission(scope = Scope.LOST_ITEM, action = Action.EDIT)
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") ItemForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        return super.update(id, form, binding, model, redirectAttributes);
    }

    @PostMapping("/{id}/archive")
    @RequirePermission(scope = Scope.LOST_ITEM, action = Action.ARCHIVE)
    public String archive(@PathVariable Long id, Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        return super.archive(id, authentication.getName(), redirectAttributes);
    }

    @Override
    @PostMapping("/{id}/delete")
    @RequirePermission(scope = Scope.LOST_ITEM, action = Action.DELETE)
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return super.delete(id, redirectAttributes);
    }
}
