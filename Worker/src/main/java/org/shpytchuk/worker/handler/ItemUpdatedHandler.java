package org.shpytchuk.worker.handler;

import org.shpytchuk.worker.event.ItemUpdatedEvent;
import org.shpytchuk.worker.service.ItemKind;

public interface ItemUpdatedHandler {

    String VERB = "updated";

    ItemKind kind();

    void onItemUpdated(ItemUpdatedEvent event);
}
