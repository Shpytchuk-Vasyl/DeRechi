package org.shpytchuk.automaticsearch.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.automaticsearch.entity.FoundItemClaim;
import org.shpytchuk.automaticsearch.entity.FoundItemHistory;
import org.shpytchuk.automaticsearch.entity.LostItemClaim;
import org.shpytchuk.automaticsearch.entity.LostItemHistory;
import org.shpytchuk.automaticsearch.entity.Thing;
import org.shpytchuk.automaticsearch.mapper.ItemMapper;
import org.shpytchuk.automaticsearch.repository.FoundItemClaimRepository;
import org.shpytchuk.automaticsearch.repository.FoundItemHistoryRepository;
import org.shpytchuk.automaticsearch.repository.FoundItemRepository;
import org.shpytchuk.automaticsearch.repository.LostItemClaimRepository;
import org.shpytchuk.automaticsearch.repository.LostItemHistoryRepository;
import org.shpytchuk.automaticsearch.repository.LostItemRepository;
import org.shpytchuk.automaticsearch.repository.SimilarItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToIntFunction;

@Service
@AllArgsConstructor
public class ItemArchiver {

    private static final Logger log = LoggerFactory.getLogger(ItemArchiver.class);

    private final LostItemRepository lostItemRepository;
    private final FoundItemRepository foundItemRepository;
    private final LostItemHistoryRepository lostItemHistoryRepository;
    private final FoundItemHistoryRepository foundItemHistoryRepository;
    private final LostItemClaimRepository lostItemClaimRepository;
    private final FoundItemClaimRepository foundItemClaimRepository;
    private final SimilarItemRepository similarItemRepository;

    @Transactional
    public boolean archive(ClaimKind kind, Long itemId) {
        return switch (kind) {
            case LOST -> archive(kind, itemId, lostItemRepository, similarItemRepository::deleteByLostItemId,
                    item -> lostItemHistoryRepository.save(stamped(ItemMapper.copy(item, new LostItemHistory()))),
                    (id, history) -> {
                        List<LostItemClaim> claims = lostItemClaimRepository.findByItemId(id);
                        claims.forEach(claim -> {
                            claim.setArchivedItem(history);
                            claim.setItem(null);
                        });
                        lostItemClaimRepository.saveAll(claims);
                        return claims.size();
                    });
            case FOUND -> archive(kind, itemId, foundItemRepository, similarItemRepository::deleteByFoundItemId,
                    item -> foundItemHistoryRepository.save(stamped(ItemMapper.copy(item, new FoundItemHistory()))),
                    (id, history) -> {
                        List<FoundItemClaim> claims = foundItemClaimRepository.findByItemId(id);
                        claims.forEach(claim -> {
                            claim.setArchivedItem(history);
                            claim.setItem(null);
                        });
                        foundItemClaimRepository.saveAll(claims);
                        return claims.size();
                    });
        };
    }

    private <T extends Thing, H extends Thing> boolean archive(ClaimKind kind,
                                                               Long itemId,
                                                               JpaRepository<T, Long> items,
                                                               ToIntFunction<Long> deleteMatches,
                                                               Function<T, H> saveHistory,
                                                               ClaimMover<H> moveClaims) {
        Optional<T> found = items.findById(itemId);
        if (found.isEmpty()) {
            log.info("{} item {} is already gone, nothing to archive", kind, itemId);
            return false;
        }

        T item = found.get();
        H history = saveHistory.apply(item);
        int matches = deleteMatches.applyAsInt(itemId);
        int claims = moveClaims.move(itemId, history);
        items.delete(item);

        log.info("Archived {} item {} as history {}: {} matches deleted, {} claims moved",
                kind, itemId, history.getId(), matches, claims);
        return true;
    }

    private static LostItemHistory stamped(LostItemHistory history) {
        history.setArchivedAt(Instant.now());
        return history;
    }

    private static FoundItemHistory stamped(FoundItemHistory history) {
        history.setArchivedAt(Instant.now());
        return history;
    }

    @FunctionalInterface
    private interface ClaimMover<H> {
        int move(Long itemId, H history);
    }
}
