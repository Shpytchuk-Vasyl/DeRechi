package org.shpytchuk.worker.service;

import org.shpytchuk.worker.event.ItemCreatedEvent;

public interface ItemCreatedHandler {

    String VERB = "created";

    ItemKind kind();

    default String routingKey() {
        return kind().routingKey(VERB);
    }

    void onItemCreated(ItemCreatedEvent event);
}
