package org.shpytchuk.adminapi.controller;

import org.shpytchuk.adminapi.config.AdminProperties;
import org.shpytchuk.adminapi.entity.Thing;
import org.shpytchuk.adminapi.form.ItemForm;
import org.shpytchuk.adminapi.security.Scope;
import org.shpytchuk.adminapi.service.AdminItemService;
import org.shpytchuk.adminapi.view.ItemView;
import org.shpytchuk.adminapi.view.Plural;
import org.springframework.data.domain.Page;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

public abstract class ItemController<T extends Thing> {

    private static final String LIST_VIEW = "items/list";
    private static final String FORM_VIEW = "items/form";

    private final AdminItemService<T> service;
    private final ItemModel itemModel;
    private final AdminProperties properties;
    private final Scope scope;
    private final String basePath;
    private final String title;

    protected ItemController(AdminItemService<T> service,
                             ItemModel itemModel,
                             AdminProperties properties,
                             Scope scope,
                             String basePath,
                             String title) {
        this.service = service;
        this.itemModel = itemModel;
        this.properties = properties;
        this.scope = scope;
        this.basePath = basePath;
        this.title = title;
    }

    protected String list(int page, Model model) {
        describe(model);
        Page<ItemView> items = service.page(page, properties.pageSize());
        model.addAttribute("items", items);
        model.addAttribute("itemsLabel", Plural.records(items.getTotalElements()));
        return LIST_VIEW;
    }

    protected String createForm(Model model) {
        describe(model);
        itemModel.forForm(model, true);
        model.addAttribute("form", new ItemForm());
        return FORM_VIEW;
    }

    protected String create(ItemForm form,
                            BindingResult binding,
                            Model model,
                            RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            describe(model);
            itemModel.forForm(model, true);
            return FORM_VIEW;
        }

        Long id = service.create(form).getId();
        return redirectWithMessage(redirectAttributes, id, "створено");
    }

    protected String editForm(Long id, Model model) {
        describe(model);
        itemModel.forForm(model, false);
        model.addAttribute("form", service.form(id));
        return FORM_VIEW;
    }

    protected String update(Long id,
                            ItemForm form,
                            BindingResult binding,
                            Model model,
                            RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            describe(model);
            itemModel.forForm(model, false);
            return FORM_VIEW;
        }

        service.update(id, form);
        return redirectWithMessage(redirectAttributes, id, "оновлено");
    }

    protected String delete(Long id, RedirectAttributes redirectAttributes) {
        service.delete(id);
        return redirectWithMessage(redirectAttributes, id, "видалено");
    }

    private void describe(Model model) {
        itemModel.describe(model, scope, basePath, title);
    }

    private String redirectWithMessage(RedirectAttributes redirectAttributes, Long id, String action) {
        redirectAttributes.addFlashAttribute("message",
                "%s #%d %s.".formatted(service.entityName(), id, action));
        return "redirect:" + basePath;
    }
}
