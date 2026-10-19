package org.shpytchuk.worker.handler;

import org.junit.jupiter.api.Test;
import org.shpytchuk.worker.entity.found.FoundItem;
import org.shpytchuk.worker.entity.lost.LostItem;
import org.shpytchuk.worker.entity.thing.Thing;
import org.mockito.InOrder;
import org.shpytchuk.worker.event.ItemCreatedEvent;
import org.shpytchuk.worker.event.ItemUpdatedEvent;
import org.shpytchuk.worker.repository.SimilarItemRepository;
import org.shpytchuk.worker.repository.found.FoundItemClaimRepository;
import org.shpytchuk.worker.repository.found.FoundItemHistoryRepository;
import org.shpytchuk.worker.repository.found.FoundItemRepository;
import org.shpytchuk.worker.repository.lost.LostItemClaimRepository;
import org.shpytchuk.worker.repository.lost.LostItemHistoryRepository;
import org.shpytchuk.worker.repository.lost.LostItemRepository;
import org.shpytchuk.worker.service.ItemKind;
import org.shpytchuk.worker.service.ItemService;
import org.shpytchuk.worker.service.KindOps;
import org.shpytchuk.worker.service.MatchCandidate;
import org.shpytchuk.worker.support.Fixtures;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;


class ItemMatchHandlerTest {

    private final SimilarItemRepository similarItems =
            mock(SimilarItemRepository.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

    @Test
    void handlesTheKindOfItsOwnNotices() {
        assertThat(new ItemMatchHandler<>(lost(), search(FoundItem.class)).kind()).isEqualTo(ItemKind.LOST);
        assertThat(new ItemMatchHandler<>(found(), search(LostItem.class)).kind()).isEqualTo(ItemKind.FOUND);
    }

    @Test
    void anEditedLostItemDropsItsUnnotifiedCandidatesAndSearchesAgain() {
        ItemService<FoundItem> foundItemSearch = search(FoundItem.class, new MatchCandidate(10L, 0.9));
        doReturn(2).when(similarItems).deleteUnnotifiedByLostItemId(7L);
        doReturn(1).when(similarItems).insertAll(any(), any(), any());

        new ItemMatchHandler<>(lost(), foundItemSearch).onItemUpdated(updatedEvent(7L));

        InOrder inOrder = inOrder(similarItems);
        inOrder.verify(similarItems).deleteUnnotifiedByLostItemId(7L);
        inOrder.verify(similarItems).insertAll(new Long[]{10L}, new Long[]{7L}, new Double[]{0.9});
        verify(similarItems, never()).deleteByLostItemId(any());
    }

    @Test
    void anEditedFoundItemDropsItsUnnotifiedCandidatesEvenWhenNothingMatchesNow() {
        doReturn(1).when(similarItems).deleteUnnotifiedByFoundItemId(8L);

        new ItemMatchHandler<>(found(), search(LostItem.class)).onItemUpdated(updatedEvent(8L));

        verify(similarItems).deleteUnnotifiedByFoundItemId(8L);
        verify(similarItems, never()).insertAll(any(), any(), any());
    }

    @Test
    void aNewLostItemTakesTheLostColumnAndItsFoundCandidatesTheOther() {
        ItemService<FoundItem> foundItemSearch = search(FoundItem.class,
                new MatchCandidate(10L, 0.9), new MatchCandidate(11L, 0.5));
        doReturn(2).when(similarItems).insertAll(any(), any(), any());

        new ItemMatchHandler<>(lost(), foundItemSearch).onItemCreated(event(7L));

        verify(similarItems).insertAll(new Long[]{10L, 11L}, new Long[]{7L, 7L}, new Double[]{0.9, 0.5});
    }

    @Test
    void aNewFoundItemTakesTheFoundColumnAndItsLostCandidatesTheOther() {
        ItemService<LostItem> lostItemSearch = search(LostItem.class, new MatchCandidate(20L, 0.0));
        doReturn(1).when(similarItems).insertAll(any(), any(), any());

        new ItemMatchHandler<>(found(), lostItemSearch).onItemCreated(event(8L));

        verify(similarItems).insertAll(new Long[]{8L}, new Long[]{20L}, new Double[]{0.0});
    }

    @Test
    void writesNothingWhenNothingMatches() {
        new ItemMatchHandler<>(lost(), search(FoundItem.class)).onItemCreated(event(7L));

        verify(similarItems, never()).insertAll(any(), any(), any());
    }

    private KindOps<?, ?, ?> lost() {
        return KindOps.lost(mock(LostItemRepository.class), mock(LostItemHistoryRepository.class),
                mock(LostItemClaimRepository.class), similarItems);
    }

    private KindOps<?, ?, ?> found() {
        return KindOps.found(mock(FoundItemRepository.class), mock(FoundItemHistoryRepository.class),
                mock(FoundItemClaimRepository.class), similarItems);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Thing> ItemService<T> search(Class<T> type, MatchCandidate... candidates) {
        ItemService<T> search = mock(ItemService.class);
        when(search.findMostSuitable(any())).thenReturn(List.of(candidates));
        return search;
    }

    private static ItemCreatedEvent event(long id) {
        return Fixtures.created(id, "Wallet", LocalDate.of(2026, 9, 10), 1L, 49.8419, 24.0315);
    }

    private static ItemUpdatedEvent updatedEvent(long id) {
        ItemUpdatedEvent event = new ItemUpdatedEvent();
        event.setId(id);
        event.setTitle("Wallet");
        event.setDate(LocalDate.of(2026, 9, 10));
        event.setCategory(1L);
        event.setLat(49.8419);
        event.setLon(24.0315);
        return event;
    }
}
