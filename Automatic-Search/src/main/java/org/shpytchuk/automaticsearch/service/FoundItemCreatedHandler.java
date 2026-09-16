package org.shpytchuk.automaticsearch.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.automaticsearch.entity.LostItem;
import org.shpytchuk.automaticsearch.entity.Thing;
import org.shpytchuk.automaticsearch.event.ItemCreatedEvent;
import org.shpytchuk.automaticsearch.repository.SimilarItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@AllArgsConstructor
public class FoundItemCreatedHandler implements ItemCreatedHandler {

    private static final Logger log = LoggerFactory.getLogger(FoundItemCreatedHandler.class);

    private static final String ROUTING_KEY = "item.found.created";

    private final  ItemService<LostItem> lostItemSearch;
    private final SimilarItemRepository similarItemRepository;

    @Override
    public String routingKey() {
        return ROUTING_KEY;
    }

    @Override
    @Transactional
    public void onItemCreated(ItemCreatedEvent event) {
        log.info("Searching for lost items for the found {}", event.getId());

        List<LostItem> candidates = lostItemSearch.findAllMostSuitable(event).getContent();

        if (candidates.isEmpty()) {
            log.info("No candidates found for the founded item {}", event.getId());
            return;
        }

        Long[] lostItemIds = candidates.stream().map(Thing::getId).toArray(Long[]::new);
        Long[] foundItemIds = new Long[lostItemIds.length];
        Arrays.fill(foundItemIds, event.getId());
        Double[] matchOrders = candidates.stream().map(Thing::getOrderMatch).toArray(Double[]::new);

        int inserted = similarItemRepository.insertAll(foundItemIds, lostItemIds, matchOrders);
        log.info("Saved {} candidates for the founded {}", inserted, event.getId());
    }
}
