package org.shpytchuk.adminapi.controller;

import org.shpytchuk.adminapi.entity.found.FoundItemHistory;
import org.shpytchuk.adminapi.form.ItemFormValidator;
import org.shpytchuk.adminapi.model.ItemModel;
import org.shpytchuk.adminapi.service.ItemKind;
import org.shpytchuk.adminapi.service.found.FoundItemHistoryAdminService;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping(ItemKind.Path.FOUND_ITEMS_HISTORY)
public class FoundItemHistoryController extends ItemController<FoundItemHistory> {

    public FoundItemHistoryController(FoundItemHistoryAdminService service, ItemModel itemModel,
                                      ItemFormValidator formValidator, MessageSource messages) {
        super(service, itemModel, formValidator, messages, ItemKind.FOUND_ITEM_HISTORY);
    }
}
