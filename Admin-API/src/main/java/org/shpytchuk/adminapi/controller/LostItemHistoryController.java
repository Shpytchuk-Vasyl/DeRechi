package org.shpytchuk.adminapi.controller;

import org.shpytchuk.adminapi.entity.lost.LostItemHistory;
import org.shpytchuk.adminapi.form.ItemFormValidator;
import org.shpytchuk.adminapi.model.ItemModel;
import org.shpytchuk.adminapi.service.ItemKind;
import org.shpytchuk.adminapi.service.lost.LostItemHistoryAdminService;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping(ItemKind.Path.LOST_ITEMS_HISTORY)
public class LostItemHistoryController extends ItemController<LostItemHistory> {

    public LostItemHistoryController(LostItemHistoryAdminService service, ItemModel itemModel,
                                     ItemFormValidator formValidator, MessageSource messages) {
        super(service, itemModel, formValidator, messages, ItemKind.LOST_ITEM_HISTORY);
    }
}
