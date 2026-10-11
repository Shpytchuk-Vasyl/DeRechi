package org.shpytchuk.worker.handler;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.worker.entity.thing.Thing;
import org.shpytchuk.worker.event.ItemCreatedEvent;
import org.shpytchuk.worker.service.ItemKind;
import org.shpytchuk.worker.service.ItemService;
import org.shpytchuk.worker.service.KindOps;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@AllArgsConstructor
@Slf4j
public class ItemMatchHandler<T extends Thing> implements ItemCreatedHandler {

    private final KindOps<?, ?, ?> own;
    private final ItemService<T> candidateSearch;

    @Override
    public ItemKind kind() {
        return own.kind();
    }

    @Override
    @Transactional
    public void onItemCreated(ItemCreatedEvent event) {
        String kind = own.kind().segment();
        log.info("Searching {} items matching {} item {}", own.kind().opposite().segment(), kind, event.getId());

        List<T> candidates = candidateSearch.findAllMostSuitable(event).getContent();

        if (candidates.isEmpty()) {
            log.info("No candidates for {} item {}", kind, event.getId());
            return;
        }

        Long[] candidateIds = candidates.stream().map(Thing::getId).toArray(Long[]::new);
        Double[] matchOrders = candidates.stream().map(Thing::getOrderMatch).toArray(Double[]::new);

        int inserted = own.insertMatches(event.getId(), candidateIds, matchOrders);
        log.info("Saved {} candidates for {} item {}", inserted, kind, event.getId());
    }
}
