package org.shpytchuk.adminapi.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.adminapi.config.cache.CachedPage;
import org.shpytchuk.adminapi.entity.thing.Thing;
import org.shpytchuk.adminapi.form.ItemFilter;
import org.shpytchuk.adminapi.form.ItemForm;
import org.shpytchuk.adminapi.form.ItemFormValidator;
import org.shpytchuk.adminapi.model.ItemModel;
import org.shpytchuk.adminapi.security.Action;
import org.shpytchuk.adminapi.security.RequireAction;
import org.shpytchuk.adminapi.security.Scope;
import org.shpytchuk.adminapi.service.AdminItemService;
import org.shpytchuk.adminapi.service.ItemKind;
import org.shpytchuk.adminapi.view.DeletedItem;
import org.shpytchuk.adminapi.view.Pager;
import org.shpytchuk.adminapi.view.SortView;
import org.shpytchuk.adminapi.view.detail.ItemView;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The pages of one kind of notice: list, create and edit forms, delete. The subclass only names its
 * {@link ItemKind} and carries the {@code @RequestMapping}; the handlers, their mappings and their
 * {@link RequireAction} live here once, and the scope they require comes from {@link #scope()}.
 * Live kinds extend {@link ArchivableItemController}, which adds the archive route.
 * <p>
 * Deliberately no interface: with one, method security could proxy the controller through a JDK proxy
 * that is no longer a {@code @Controller}, and the routes would vanish.
 */
@CachedPage
@Slf4j
public abstract class ItemController<T extends Thing> {

    private static final String LIST_VIEW = "items/list";
    private static final String FORM_VIEW = "items/form";

    private final AdminItemService<T> service;
    private final ItemModel itemModel;
    private final ItemFormValidator formValidator;
    private final MessageSource messages;
    private final ItemKind kind;

    protected ItemController(AdminItemService<T> service,
                             ItemModel itemModel,
                             ItemFormValidator formValidator,
                             MessageSource messages,
                             ItemKind kind) {
        this.service = service;
        this.itemModel = itemModel;
        this.formValidator = formValidator;
        this.messages = messages;
        this.kind = kind;
    }

    public Scope scope() {
        return kind.scope();
    }

    protected ItemKind kind() {
        return kind;
    }

    @InitBinder("form")
    void bindForm(WebDataBinder binder) {
        binder.addValidators(formValidator);
    }

    @GetMapping
    @RequireAction(Action.VIEW)
    public String list(Pageable pageable,
                       @ModelAttribute("filter") ItemFilter filter,
                       Model model) {
        describe(model);
        Page<ItemView> items = service.page(pageable, filter);
        model.addAttribute("items", items);
        model.addAttribute("claims", service.claims(items.getContent().stream().map(ItemView::id).toList()));
        model.addAttribute("pages", Pager.of(items));
        itemModel.forSources(model, kind.scope(), items.getContent().stream().map(ItemView::id).toList());

        SortView sort = SortView.of(items.getSort());
        model.addAttribute("sort", sort);
        model.addAttribute("sortQuery", sort.queryString());
        itemModel.forFilter(model, filter);
        return LIST_VIEW;
    }

    @GetMapping("/new")
    @RequireAction(Action.CREATE)
    public String createForm(Model model) {
        describe(model);
        itemModel.forForm(model, true);

        model.addAttribute("form", itemModel.blankForm());
        return FORM_VIEW;
    }

    @PostMapping
    @RequireAction(Action.CREATE)
    public String create(@Valid @ModelAttribute("form") ItemForm form,
                         BindingResult binding,
                         Model model,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            describe(model);
            itemModel.forForm(model, true);
            return FORM_VIEW;
        }

        Long id = service.create(form).getId();
        log.info("{} created {} {}", authentication.getName(), kind.label(), id);
        return redirectWithMessage(redirectAttributes, id, "created");
    }

    @GetMapping("/{id}/edit")
    @RequireAction(Action.EDIT)
    public String editForm(@PathVariable Long id, Model model) {
        describe(model);
        itemModel.forForm(model, false);
        model.addAttribute("form", service.form(id));
        return FORM_VIEW;
    }

    @PostMapping("/{id}")
    @RequireAction(Action.EDIT)
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") ItemForm form,
                         BindingResult binding,
                         Model model,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            describe(model);
            itemModel.forForm(model, false);
            return FORM_VIEW;
        }

        service.update(id, form);
        log.info("{} updated {} {}", authentication.getName(), kind.label(), id);
        return redirectWithMessage(redirectAttributes, id, "updated");
    }

    @PostMapping("/{id}/delete")
    @RequireAction(Action.DELETE)
    public String delete(@PathVariable Long id, Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        DeletedItem deleted = service.delete(id);
        log.info("{} deleted {} {} with {} matches and {} claims",
                authentication.getName(), kind.label(), id, deleted.matches(), deleted.claims());
        return redirectWithMessage(redirectAttributes, id, "deleted");
    }

    private void describe(Model model) {
        itemModel.describe(model, kind);
    }

    protected String redirectWithMessage(RedirectAttributes redirectAttributes, Long id, String action) {
        redirectAttributes.addFlashAttribute("message",
                messages.getMessage(kind.flashKey(action), new Object[]{id}, LocaleContextHolder.getLocale()));
        return "redirect:" + kind.basePath();
    }
}
