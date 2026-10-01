package org.shpytchuk.automaticsearch.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.automaticsearch.entity.Claim;
import org.shpytchuk.automaticsearch.entity.FoundItemHistory;
import org.shpytchuk.automaticsearch.entity.LostItemHistory;
import org.shpytchuk.automaticsearch.entity.Thing;
import org.shpytchuk.automaticsearch.mapper.ItemMapper;
import org.shpytchuk.automaticsearch.repository.ClaimRepository;
import org.shpytchuk.automaticsearch.repository.FoundItemHistoryRepository;
import org.shpytchuk.automaticsearch.repository.FoundItemRepository;
import org.shpytchuk.automaticsearch.repository.LostItemHistoryRepository;
import org.shpytchuk.automaticsearch.repository.LostItemRepository;
import org.shpytchuk.automaticsearch.repository.SimilarItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class ItemArchiver {

    private static final Logger log = LoggerFactory.getLogger(ItemArchiver.class);

    private final LostItemRepository lostItemRepository;
    private final FoundItemRepository foundItemRepository;
    private final LostItemHistoryRepository lostItemHistoryRepository;
    private final FoundItemHistoryRepository foundItemHistoryRepository;
    private final SimilarItemRepository similarItemRepository;
    private final ClaimRepositories claimRepositories;
    private final Clock clock;

    @Transactional
    public boolean archive(ItemKind kind, Long itemId) {
        return switch (kind) {
            case LOST -> archive(kind, itemId, lostItemRepository);
            case FOUND -> archive(kind, itemId, foundItemRepository);
        };
    }

    private <T extends Thing> boolean archive(ItemKind kind, Long itemId, JpaRepository<T, Long> items) {
        Optional<T> found = items.findById(itemId);
        if (found.isEmpty()) {
            log.info("{} item {} is already gone, nothing to archive", kind, itemId);
            return false;
        }

        T item = found.get();
        Thing history = saveHistory(kind, item);
        int matches = deleteMatches(kind, itemId);
        int claims = moveClaims(claimRepositories.of(kind), itemId, history);
        items.delete(item);

        log.info("Archived {} item {} as history {}: {} matches deleted, {} claims moved",
                kind, itemId, history.getId(), matches, claims);
        return true;
    }

    private Thing saveHistory(ItemKind kind, Thing item) {
        return switch (kind) {
            case LOST -> {
                LostItemHistory history = ItemMapper.copy(item, new LostItemHistory());
                history.setArchivedAt(clock.instant());
                yield lostItemHistoryRepository.save(history);
            }
            case FOUND -> {
                FoundItemHistory history = ItemMapper.copy(item, new FoundItemHistory());
                history.setArchivedAt(clock.instant());
                yield foundItemHistoryRepository.save(history);
            }
        };
    }

    private int deleteMatches(ItemKind kind, Long itemId) {
        return switch (kind) {
            case LOST -> similarItemRepository.deleteByLostItemId(itemId);
            case FOUND -> similarItemRepository.deleteByFoundItemId(itemId);
        };
    }

    private static <C extends Claim> int moveClaims(ClaimRepository<C> claims, Long itemId, Thing history) {
        List<C> moved = claims.findByItemId(itemId);
        moved.forEach(claim -> claim.moveToArchive(history));
        claims.saveAll(moved);
        return moved.size();
    }
}
