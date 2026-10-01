package org.shpytchuk.automaticsearch.service;

import org.shpytchuk.automaticsearch.event.ItemCreatedEvent;

public interface ItemCreatedHandler {

    String VERB = "created";

    ItemKind kind();

    default String routingKey() {
        return kind().routingKey(VERB);
    }

    void onItemCreated(ItemCreatedEvent event);
}
