package org.shpytchuk.worker.service;

import org.shpytchuk.worker.entity.matching.Claim;
import org.shpytchuk.worker.repository.ClaimRepository;
import org.shpytchuk.worker.repository.SimilarItemRepository;
import org.shpytchuk.worker.repository.found.FoundItemClaimRepository;
import org.shpytchuk.worker.repository.found.FoundItemHistoryRepository;
import org.shpytchuk.worker.repository.found.FoundItemRepository;
import org.shpytchuk.worker.repository.lost.LostItemClaimRepository;
import org.shpytchuk.worker.repository.lost.LostItemHistoryRepository;
import org.shpytchuk.worker.repository.lost.LostItemRepository;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class ItemKinds {

    private final Map<ItemKind, KindOps<?, ?, ?>> byKind = new EnumMap<>(ItemKind.class);

    public ItemKinds(LostItemRepository lostItems,
                     LostItemHistoryRepository lostHistories,
                     LostItemClaimRepository lostClaims,
                     FoundItemRepository foundItems,
                     FoundItemHistoryRepository foundHistories,
                     FoundItemClaimRepository foundClaims,
                     SimilarItemRepository matches) {
        byKind.put(ItemKind.LOST, KindOps.lost(lostItems, lostHistories, lostClaims, matches));
        byKind.put(ItemKind.FOUND, KindOps.found(foundItems, foundHistories, foundClaims, matches));
    }

    public KindOps<?, ?, ?> of(ItemKind kind) {
        return byKind.get(kind);
    }

    public ClaimRepository<? extends Claim> claims(ItemKind kind) {
        return of(kind).claims();
    }
}
