package org.shpytchuk.worker.handler;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.worker.entity.found.FoundItem;
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
public class LostItemCreatedHandler implements ItemCreatedHandler {

    private final ItemService<FoundItem> foundItemSearch;
    private final SimilarItemRepository similarItemRepository;

    @Override
    public ItemKind kind() {
        return ItemKind.LOST;
    }

    @Override
    @Transactional
    public void onItemCreated(ItemCreatedEvent event) {
        log.info("Searching found items matching lost item {}", event.getId());

        List<FoundItem> candidates = foundItemSearch.findAllMostSuitable(event).getContent();

        if (candidates.isEmpty()) {
            log.info("No candidates for lost item {}", event.getId());
            return;
        }

        Long[] foundItemIds = candidates.stream().map(Thing::getId).toArray(Long[]::new);
        Long[] lostItemIds = new Long[foundItemIds.length];
        Arrays.fill(lostItemIds, event.getId());
        Double[] matchOrders = candidates.stream().map(Thing::getOrderMatch).toArray(Double[]::new);

        int inserted = similarItemRepository.insertAll(foundItemIds, lostItemIds, matchOrders);
        log.info("Saved {} candidates for lost item {}", inserted, event.getId());
    }
}
