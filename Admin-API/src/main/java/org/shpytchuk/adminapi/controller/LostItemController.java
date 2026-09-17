package org.shpytchuk.adminapi.controller;

import jakarta.validation.Valid;
import org.shpytchuk.adminapi.config.AdminProperties;
import org.shpytchuk.adminapi.entity.items.LostItem;
import org.shpytchuk.adminapi.form.ItemForm;
import org.shpytchuk.adminapi.security.Action;
import org.shpytchuk.adminapi.security.RequirePermission;
import org.shpytchuk.adminapi.security.Scope;
import org.shpytchuk.adminapi.service.LostItemAdminService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping(LostItemController.BASE_PATH)
public class LostItemController extends ItemController<LostItem> {

    static final String BASE_PATH = "/admin/lost-items";
    private static final String TITLE = "Загублені речі";

    public LostItemController(LostItemAdminService service, ItemModel itemModel, AdminProperties properties) {
        super(service, itemModel, properties, Scope.LOST_ITEM, BASE_PATH, TITLE);
    }

    @Override
    @GetMapping
    @RequirePermission(scope = Scope.LOST_ITEM, action = Action.VIEW)
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        return super.list(page, model);
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

    @Override
    @PostMapping("/{id}/delete")
    @RequirePermission(scope = Scope.LOST_ITEM, action = Action.DELETE)
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return super.delete(id, redirectAttributes);
    }
}
