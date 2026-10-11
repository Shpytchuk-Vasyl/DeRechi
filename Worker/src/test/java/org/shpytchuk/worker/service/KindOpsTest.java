package org.shpytchuk.worker.service;

import org.junit.jupiter.api.Test;
import org.shpytchuk.worker.entity.found.FoundItem;
import org.shpytchuk.worker.entity.found.FoundItemClaim;
import org.shpytchuk.worker.entity.found.FoundItemHistory;
import org.shpytchuk.worker.entity.lost.LostItem;
import org.shpytchuk.worker.entity.lost.LostItemClaim;
import org.shpytchuk.worker.entity.lost.LostItemHistory;
import org.shpytchuk.worker.repository.SimilarItemRepository;
import org.shpytchuk.worker.repository.found.FoundItemClaimRepository;
import org.shpytchuk.worker.repository.found.FoundItemHistoryRepository;
import org.shpytchuk.worker.repository.found.FoundItemRepository;
import org.shpytchuk.worker.repository.lost.LostItemClaimRepository;
import org.shpytchuk.worker.repository.lost.LostItemHistoryRepository;
import org.shpytchuk.worker.repository.lost.LostItemRepository;
import org.shpytchuk.worker.support.Fixtures;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Each kind owns its own column of similar_item and its own history table. */
class KindOpsTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final Long[] CANDIDATES = {10L, 11L};
    private static final Double[] RANKS = {0.9, 0.5};

    private final SimilarItemRepository matches = mock(SimilarItemRepository.class);
    private final LostItemHistoryRepository lostHistories = mock(LostItemHistoryRepository.class);
    private final FoundItemHistoryRepository foundHistories = mock(FoundItemHistoryRepository.class);

    private final KindOps<LostItem, LostItemHistory, LostItemClaim> lost = KindOps.lost(
            mock(LostItemRepository.class), lostHistories, mock(LostItemClaimRepository.class), matches);
    private final KindOps<FoundItem, FoundItemHistory, FoundItemClaim> found = KindOps.found(
            mock(FoundItemRepository.class), foundHistories, mock(FoundItemClaimRepository.class), matches);

    @Test
    void theLostKindWritesAndDeletesTheLostColumn() {
        lost.insertMatches(7L, CANDIDATES, RANKS);
        lost.deleteMatches(7L);

        verify(matches).insertForLost(7L, CANDIDATES, RANKS);
        verify(matches).deleteByLostItemId(7L);
        verify(matches, never()).insertForFound(any(), any(), any());
        verify(matches, never()).deleteByFoundItemId(any());
    }

    @Test
    void theFoundKindWritesAndDeletesTheFoundColumn() {
        found.insertMatches(8L, CANDIDATES, RANKS);
        found.deleteMatches(8L);

        verify(matches).insertForFound(8L, CANDIDATES, RANKS);
        verify(matches).deleteByFoundItemId(8L);
        verify(matches, never()).insertForLost(any(), any(), any());
        verify(matches, never()).deleteByLostItemId(any());
    }

    @Test
    void savesAStampedHistoryCopyInTheTableOfTheKind() {
        when(foundHistories.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        FoundItem item = (FoundItem) Fixtures.foundClaim("+380671234567").getItem();
        item.setCurrency("UAH");

        FoundItemHistory history = found.saveHistory(item, NOW);

        assertThat(history.getTitle()).isEqualTo("Black backpack");
        assertThat(history.getCurrency()).isEqualTo("UAH");
        assertThat(history.getInfo()).isSameAs(item.getInfo());
        assertThat(history.getArchivedAt()).isEqualTo(NOW);
        verify(foundHistories).save(history);
        verify(lostHistories, never()).save(any());
    }

    @Test
    void knowsItsKind() {
        assertThat(lost.kind()).isEqualTo(ItemKind.LOST);
        assertThat(found.kind()).isEqualTo(ItemKind.FOUND);
    }
}
