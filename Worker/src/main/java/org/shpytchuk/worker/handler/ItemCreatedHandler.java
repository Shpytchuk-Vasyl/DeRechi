package org.shpytchuk.worker.handler;

import org.shpytchuk.worker.event.ItemCreatedEvent;
import org.shpytchuk.worker.service.ItemKind;

public interface ItemCreatedHandler {

    String VERB = "created";

    ItemKind kind();

    default String routingKey() {
        return kind().routingKey(VERB);
    }

    void onItemCreated(ItemCreatedEvent event);
}
