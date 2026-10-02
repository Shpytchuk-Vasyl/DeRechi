package org.shpytchuk.adminapi.controller;

import org.shpytchuk.adminapi.config.cache.CachedPage;
import org.shpytchuk.adminapi.entity.thing.Thing;
import org.shpytchuk.adminapi.form.ItemFilter;
import org.shpytchuk.adminapi.form.ItemForm;
import org.shpytchuk.adminapi.form.ItemFormValidator;
import org.shpytchuk.adminapi.model.ItemModel;
import org.shpytchuk.adminapi.security.Scope;
import org.shpytchuk.adminapi.service.AdminItemService;
import org.shpytchuk.adminapi.view.detail.ItemView;
import org.shpytchuk.adminapi.view.Pager;
import org.shpytchuk.adminapi.view.SortView;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@CachedPage
public abstract class ItemController<T extends Thing> {

    private static final String LIST_VIEW = "items/list";
    private static final String FORM_VIEW = "items/form";

    private final AdminItemService<T> service;
    private final ItemModel itemModel;
    private final ItemFormValidator formValidator;
    private final MessageSource messages;
    private final Scope scope;
    private final String basePath;
    private final String titleKey;

    protected ItemController(AdminItemService<T> service,
                             ItemModel itemModel,
                             ItemFormValidator formValidator,
                             MessageSource messages,
                             Scope scope,
                             String basePath,
                             String titleKey) {
        this.service = service;
        this.itemModel = itemModel;
        this.formValidator = formValidator;
        this.messages = messages;
        this.scope = scope;
        this.basePath = basePath;
        this.titleKey = titleKey;
    }

    @InitBinder("form")
    void bindForm(WebDataBinder binder) {
        binder.addValidators(formValidator);
    }

    protected String list(Pageable pageable, ItemFilter filter, Model model) {
        describe(model);
        Page<ItemView> items = service.page(pageable, filter);
        model.addAttribute("items", items);
        model.addAttribute("claims", service.claims(items.getContent().stream().map(ItemView::id).toList()));
        model.addAttribute("pages", Pager.of(items));

        SortView sort = SortView.of(items.getSort());
        model.addAttribute("sort", sort);
        model.addAttribute("sortQuery", sort.queryString());
        itemModel.forFilter(model, filter);
        return LIST_VIEW;
    }

    protected String createForm(Model model) {
        describe(model);
        itemModel.forForm(model, true);

        model.addAttribute("form", itemModel.blankForm());
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
        return redirectWithMessage(redirectAttributes, id, "created");
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
        return redirectWithMessage(redirectAttributes, id, "updated");
    }

    protected String archive(Long id, String actor, RedirectAttributes redirectAttributes) {
        service.archive(id, actor);
        return redirectWithMessage(redirectAttributes, id, "archived");
    }

    protected String delete(Long id, RedirectAttributes redirectAttributes) {
        service.delete(id);
        return redirectWithMessage(redirectAttributes, id, "deleted");
    }

    private void describe(Model model) {
        itemModel.describe(model, scope, basePath, titleKey);
    }

    private String redirectWithMessage(RedirectAttributes redirectAttributes, Long id, String action) {
        String key = "flash.%s.%s".formatted(service.scopeKey().toLowerCase(), action);
        redirectAttributes.addFlashAttribute("message",
                messages.getMessage(key, new Object[]{id}, LocaleContextHolder.getLocale()));
        return "redirect:" + basePath;
    }
}
