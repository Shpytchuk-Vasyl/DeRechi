package org.shpytchuk.adminapi.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.shpytchuk.adminapi.config.AdminProperties;
import org.shpytchuk.adminapi.form.ItemForm;
import org.shpytchuk.adminapi.security.Action;
import org.shpytchuk.adminapi.security.RequirePermission;
import org.shpytchuk.adminapi.security.Scope;
import org.shpytchuk.adminapi.service.FoundItemAdminService;
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
@RequestMapping(FoundItemController.BASE_PATH)
@AllArgsConstructor
public class FoundItemController {

    static final String BASE_PATH = "/admin/found-items";
    private static final String TITLE = "Знайдені речі";

    private final FoundItemAdminService service;
    private final ItemModel itemModel;
    private final AdminProperties properties;

    @GetMapping
    @RequirePermission(scope = Scope.FOUND_ITEM, action = Action.VIEW)
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        describe(model);
        model.addAttribute("items", service.page(page, properties.pageSize()));
        return "items/list";
    }

    @GetMapping("/new")
    @RequirePermission(scope = Scope.FOUND_ITEM, action = Action.CREATE)
    public String createForm(Model model) {
        describe(model);
        itemModel.forForm(model, true);
        model.addAttribute("form", new ItemForm());
        return "items/form";
    }

    @PostMapping
    @RequirePermission(scope = Scope.FOUND_ITEM, action = Action.CREATE)
    public String create(@Valid @ModelAttribute("form") ItemForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            describe(model);
            itemModel.forForm(model, true);
            return "items/form";
        }

        Long id = service.create(form).getId();
        redirectAttributes.addFlashAttribute("message", "Знайдену річ #%d створено.".formatted(id));
        return "redirect:" + BASE_PATH;
    }

    @GetMapping("/{id}/edit")
    @RequirePermission(scope = Scope.FOUND_ITEM, action = Action.EDIT)
    public String editForm(@PathVariable Long id, Model model) {
        describe(model);
        itemModel.forForm(model, false);
        model.addAttribute("form", service.form(id));
        return "items/form";
    }

    @PostMapping("/{id}")
    @RequirePermission(scope = Scope.FOUND_ITEM, action = Action.EDIT)
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") ItemForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            describe(model);
            itemModel.forForm(model, false);
            return "items/form";
        }

        service.update(id, form);
        redirectAttributes.addFlashAttribute("message", "Знайдену річ #%d оновлено.".formatted(id));
        return "redirect:" + BASE_PATH;
    }

    @PostMapping("/{id}/delete")
    @RequirePermission(scope = Scope.FOUND_ITEM, action = Action.DELETE)
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        service.delete(id);
        redirectAttributes.addFlashAttribute("message", "Знайдену річ #%d видалено.".formatted(id));
        return "redirect:" + BASE_PATH;
    }

    private void describe(Model model) {
        itemModel.describe(model, Scope.FOUND_ITEM, BASE_PATH, TITLE);
    }
}
