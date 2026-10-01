package org.shpytchuk.automaticsearch.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.automaticsearch.entity.FoundItem;
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
public class LostItemCreatedHandler implements ItemCreatedHandler {

    private static final Logger log = LoggerFactory.getLogger(LostItemCreatedHandler.class);

    private final ItemService<FoundItem> foundItemSearch;
    private final SimilarItemRepository similarItemRepository;

    @Override
    public ItemKind kind() {
        return ItemKind.LOST;
    }

    @Override
    @Transactional
    public void onItemCreated(ItemCreatedEvent event) {
        log.info("Search for found items belonging to the person who lost them {}", event.getId());

        List<FoundItem> candidates = foundItemSearch.findAllMostSuitable(event).getContent();

        if (candidates.isEmpty()) {
            log.info("No candidates found for the lost {}", event.getId());
            return;
        }

        Long[] foundItemIds = candidates.stream().map(Thing::getId).toArray(Long[]::new);
        Long[] lostItemIds = new Long[foundItemIds.length];
        Arrays.fill(lostItemIds, event.getId());
        Double[] matchOrders = candidates.stream().map(Thing::getOrderMatch).toArray(Double[]::new);

        int inserted = similarItemRepository.insertAll(foundItemIds, lostItemIds, matchOrders);
        log.info("Saved {} candidates for the lost {}", inserted, event.getId());
    }
}
