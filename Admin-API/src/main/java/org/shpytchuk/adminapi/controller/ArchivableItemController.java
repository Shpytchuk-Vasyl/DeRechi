package org.shpytchuk.adminapi.controller;

import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.adminapi.entity.thing.Thing;
import org.shpytchuk.adminapi.form.ItemFormValidator;
import org.shpytchuk.adminapi.model.ItemModel;
import org.shpytchuk.adminapi.security.Action;
import org.shpytchuk.adminapi.security.RequireAction;
import org.shpytchuk.adminapi.service.ArchivableAdminItemService;
import org.shpytchuk.adminapi.service.ItemKind;
import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
public abstract class ArchivableItemController<T extends Thing> extends ItemController<T> {

    private final ArchivableAdminItemService<T> service;

    protected ArchivableItemController(ArchivableAdminItemService<T> service,
                                       ItemModel itemModel,
                                       ItemFormValidator formValidator,
                                       MessageSource messages,
                                       ItemKind kind) {
        super(service, itemModel, formValidator, messages, kind);
        this.service = service;
    }

    @PostMapping("/{id}/archive")
    @RequireAction(Action.ARCHIVE)
    public String archive(@PathVariable Long id, Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        service.archive(id, authentication.getName());
        log.info("{} requested archiving of {} {}", authentication.getName(), kind().label(), id);
        return redirectWithMessage(redirectAttributes, id, "archived");
    }
}
