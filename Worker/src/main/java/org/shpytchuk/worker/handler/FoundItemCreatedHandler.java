package org.shpytchuk.worker.handler;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.worker.entity.lost.LostItem;
import org.shpytchuk.worker.entity.thing.Thing;
import org.shpytchuk.worker.event.ItemCreatedEvent;
import org.shpytchuk.worker.repository.SimilarItemRepository;
import org.shpytchuk.worker.service.ItemKind;
import org.shpytchuk.worker.service.ItemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class FoundItemCreatedHandler implements ItemCreatedHandler {

    private final ItemService<LostItem> lostItemSearch;
    private final SimilarItemRepository similarItemRepository;

    @Override
    public ItemKind kind() {
        return ItemKind.FOUND;
    }

    @Override
    @Transactional
    public void onItemCreated(ItemCreatedEvent event) {
        log.info("Searching lost items matching found item {}", event.getId());

        List<LostItem> candidates = lostItemSearch.findAllMostSuitable(event).getContent();

        if (candidates.isEmpty()) {
            log.info("No candidates for found item {}", event.getId());
            return;
        }

        Long[] lostItemIds = candidates.stream().map(Thing::getId).toArray(Long[]::new);
        Long[] foundItemIds = new Long[lostItemIds.length];
        Arrays.fill(foundItemIds, event.getId());
        Double[] matchOrders = candidates.stream().map(Thing::getOrderMatch).toArray(Double[]::new);

        int inserted = similarItemRepository.insertAll(foundItemIds, lostItemIds, matchOrders);
        log.info("Saved {} candidates for found item {}", inserted, event.getId());
    }
}
