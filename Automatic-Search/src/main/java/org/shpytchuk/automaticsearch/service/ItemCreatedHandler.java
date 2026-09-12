package org.shpytchuk.automaticsearch.service;

import org.shpytchuk.automaticsearch.event.ItemCreatedEvent;

public interface ItemCreatedHandler {

    String routingKey();

    void onItemCreated(ItemCreatedEvent event);
}
