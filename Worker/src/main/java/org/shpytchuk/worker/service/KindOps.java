package org.shpytchuk.worker.service;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.shpytchuk.worker.entity.found.FoundItem;
import org.shpytchuk.worker.entity.found.FoundItemClaim;
import org.shpytchuk.worker.entity.found.FoundItemHistory;
import org.shpytchuk.worker.entity.lost.LostItem;
import org.shpytchuk.worker.entity.lost.LostItemClaim;
import org.shpytchuk.worker.entity.lost.LostItemHistory;
import org.shpytchuk.worker.entity.matching.Claim;
import org.shpytchuk.worker.entity.thing.Thing;
import org.shpytchuk.worker.mapper.ItemMapper;
import org.shpytchuk.worker.repository.ClaimRepository;
import org.shpytchuk.worker.repository.SimilarItemRepository;
import org.shpytchuk.worker.repository.ThingRepository;
import org.shpytchuk.worker.repository.found.FoundItemClaimRepository;
import org.shpytchuk.worker.repository.found.FoundItemHistoryRepository;
import org.shpytchuk.worker.repository.found.FoundItemRepository;
import org.shpytchuk.worker.repository.lost.LostItemClaimRepository;
import org.shpytchuk.worker.repository.lost.LostItemHistoryRepository;
import org.shpytchuk.worker.repository.lost.LostItemRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class KindOps<T extends Thing, H extends Thing, C extends Claim> {

    private final ItemKind kind;
    private final ThingRepository<T> items;
    private final JpaRepository<H, Long> histories;
    private final ClaimRepository<C> claims;
    private final Supplier<H> newHistory;
    private final BiConsumer<H, Instant> archivedAt;
    private final MatchInsert insertMatches;
    private final ToIntFunction<Long> deleteMatches;
    private final ToIntFunction<Long> deleteUnnotifiedMatches;

    public static KindOps<LostItem, LostItemHistory, LostItemClaim> lost(LostItemRepository items,
                                                                         LostItemHistoryRepository histories,
                                                                         LostItemClaimRepository claims,
                                                                         SimilarItemRepository matches) {
        return new KindOps<>(ItemKind.LOST, items, histories, claims,
                LostItemHistory::new, LostItemHistory::setArchivedAt,
                matches::insertForLost, matches::deleteByLostItemId, matches::deleteUnnotifiedByLostItemId);
    }

    public static KindOps<FoundItem, FoundItemHistory, FoundItemClaim> found(FoundItemRepository items,
                                                                             FoundItemHistoryRepository histories,
                                                                             FoundItemClaimRepository claims,
                                                                             SimilarItemRepository matches) {
        return new KindOps<>(ItemKind.FOUND, items, histories, claims,
                FoundItemHistory::new, FoundItemHistory::setArchivedAt,
                matches::insertForFound, matches::deleteByFoundItemId, matches::deleteUnnotifiedByFoundItemId);
    }

    public ItemKind kind() {
        return kind;
    }

    public ThingRepository<T> items() {
        return items;
    }

    public ClaimRepository<C> claims() {
        return claims;
    }

    public H saveHistory(T item, Instant at) {
        H history = ItemMapper.copy(item, newHistory.get());
        archivedAt.accept(history, at);
        return histories.save(history);
    }

    public int insertMatches(Long itemId, Long[] candidateIds, Double[] matchOrders) {
        return insertMatches.insert(itemId, candidateIds, matchOrders);
    }

    public int deleteMatches(Long itemId) {
        return deleteMatches.applyAsInt(itemId);
    }

    public int deleteUnnotifiedMatches(Long itemId) {
        return deleteUnnotifiedMatches.applyAsInt(itemId);
    }

    @FunctionalInterface
    interface MatchInsert {

        int insert(Long itemId, Long[] candidateIds, Double[] matchOrders);
    }
}
