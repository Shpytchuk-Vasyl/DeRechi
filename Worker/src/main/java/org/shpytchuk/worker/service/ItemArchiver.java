package org.shpytchuk.worker.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.worker.entity.matching.Claim;
import org.shpytchuk.worker.entity.thing.Thing;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
@Slf4j
public class ItemArchiver {

    private final ItemKinds kinds;
    private final Clock clock;

    @Transactional
    public boolean archive(ItemKind kind, Long itemId) {
        return archive(kinds.of(kind), itemId);
    }

    private <T extends Thing, H extends Thing, C extends Claim> boolean archive(KindOps<T, H, C> ops, Long itemId) {
        Optional<T> found = ops.items().findById(itemId);
        if (found.isEmpty()) {
            log.info("{} item {} is already gone, nothing to archive", ops.kind(), itemId);
            return false;
        }

        T item = found.get();
        H history = ops.saveHistory(item, clock.instant());
        int matches = ops.deleteMatches(itemId);
        List<C> claims = ops.claims().findByItemId(itemId);
        claims.forEach(claim -> claim.moveToArchive(history));
        ops.claims().saveAll(claims);
        ops.items().delete(item);

        log.info("Archived {} item {} as history {}: {} matches deleted, {} claims moved",
                ops.kind(), itemId, history.getId(), matches, claims.size());
        return true;
    }
}
