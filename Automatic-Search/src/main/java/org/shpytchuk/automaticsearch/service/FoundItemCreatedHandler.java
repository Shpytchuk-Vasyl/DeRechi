package org.shpytchuk.automaticsearch.service;

import org.shpytchuk.automaticsearch.event.ItemCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FoundItemCreatedHandler implements ItemCreatedHandler {

    private static final Logger log = LoggerFactory.getLogger(FoundItemCreatedHandler.class);

    private static final String ROUTING_KEY = "item.found.created";

    @Override
    public String routingKey() {
        return ROUTING_KEY;
    }

    @Override
    public void onItemCreated(ItemCreatedEvent event) {
        log.info("Пошук загублених речей для знайденої {}", event.getId());
    }
}
