package org.shpytchuk.worker.handler;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.worker.entity.thing.Thing;
import org.shpytchuk.worker.event.ItemCreatedEvent;
import org.shpytchuk.worker.event.ItemUpdatedEvent;
import org.shpytchuk.worker.service.ItemData;
import org.shpytchuk.worker.service.ItemKind;
import org.shpytchuk.worker.service.ItemService;
import org.shpytchuk.worker.service.KindOps;
import org.shpytchuk.worker.service.MatchCandidate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@AllArgsConstructor
@Slf4j
public class ItemMatchHandler<T extends Thing> implements ItemCreatedHandler, ItemUpdatedHandler {

    private final KindOps<?, ?, ?> own;
    private final ItemService<T> candidateSearch;

    @Override
    public ItemKind kind() {
        return own.kind();
    }

    @Override
    @Transactional
    public void onItemCreated(ItemCreatedEvent event) {
        match(event);
    }

    @Override
    @Transactional
    public void onItemUpdated(ItemUpdatedEvent event) {
        int dropped = own.deleteUnnotifiedMatches(event.getId());
        log.info("Dropped {} candidates of edited {} item {} nobody was notified about", dropped, own.kind().segment(),
                event.getId());
        match(event);
    }

    private void match(ItemData item) {
        String kind = own.kind().segment();
        log.info("Searching {} items matching {} item {}", own.kind().opposite().segment(), kind, item.getId());

        List<MatchCandidate> candidates = candidateSearch.findMostSuitable(item);

        if (candidates.isEmpty()) {
            log.info("No candidates for {} item {}", kind, item.getId());
            return;
        }

        Long[] candidateIds = candidates.stream().map(MatchCandidate::id).toArray(Long[]::new);
        Double[] matchOrders = candidates.stream().map(MatchCandidate::rank).toArray(Double[]::new);

        int inserted = own.insertMatches(item.getId(), candidateIds, matchOrders);
        log.info("Saved {} candidates for {} item {}", inserted, kind, item.getId());
    }
}
