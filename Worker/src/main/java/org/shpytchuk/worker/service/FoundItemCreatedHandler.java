package org.shpytchuk.worker.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.worker.entity.LostItem;
import org.shpytchuk.worker.entity.Thing;
import org.shpytchuk.worker.event.ItemCreatedEvent;
import org.shpytchuk.worker.repository.SimilarItemRepository;
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

    private final  ItemService<LostItem> lostItemSearch;
    private final SimilarItemRepository similarItemRepository;

    @Override
    public ItemKind kind() {
        return ItemKind.FOUND;
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
