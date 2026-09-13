package org.shpytchuk.automaticsearch.service;

import org.shpytchuk.automaticsearch.event.ItemCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LostItemCreatedHandler implements ItemCreatedHandler {

    private static final Logger log = LoggerFactory.getLogger(LostItemCreatedHandler.class);

    private static final String ROUTING_KEY = "item.lost.created";

    @Override
    public String routingKey() {
        return ROUTING_KEY;
    }

    @Override
    public void onItemCreated(ItemCreatedEvent event) {
        log.info("Пошук знайдених речей для загубленої {}", event.getId());
    }
}
