package org.shpytchuk.automaticsearch.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.shpytchuk.automaticsearch.entity.LostItem;
import org.shpytchuk.automaticsearch.entity.LostItemClaim;
import org.shpytchuk.automaticsearch.entity.LostItemHistory;
import org.shpytchuk.automaticsearch.repository.FoundItemClaimRepository;
import org.shpytchuk.automaticsearch.repository.FoundItemHistoryRepository;
import org.shpytchuk.automaticsearch.repository.FoundItemRepository;
import org.shpytchuk.automaticsearch.repository.LostItemClaimRepository;
import org.shpytchuk.automaticsearch.repository.LostItemHistoryRepository;
import org.shpytchuk.automaticsearch.repository.LostItemRepository;
import org.shpytchuk.automaticsearch.repository.SimilarItemRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemArchiverTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    private LostItemRepository lostItems;
    private LostItemHistoryRepository lostHistory;
    private LostItemClaimRepository lostClaims;
    private SimilarItemRepository similarItems;
    private ItemArchiver archiver;

    @BeforeEach
    void setUp() {
        lostItems = mock(LostItemRepository.class);
        lostHistory = mock(LostItemHistoryRepository.class);
        lostClaims = mock(LostItemClaimRepository.class);
        similarItems = mock(SimilarItemRepository.class);
        archiver = new ItemArchiver(lostItems, mock(FoundItemRepository.class), lostHistory,
                mock(FoundItemHistoryRepository.class), similarItems,
                new ClaimRepositories(lostClaims, mock(FoundItemClaimRepository.class)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void copiesTheItemAndRepointsItsClaimsAtTheCopy() {
        LostItemClaim claim = ClaimNotifierTest.lostClaim("+380671234567");
        LostItem item = (LostItem) claim.getItem();
        item.setCurrency("UAH");
        when(lostItems.findById(1L)).thenReturn(Optional.of(item));
        when(lostHistory.save(any())).thenAnswer(invocation -> {
            LostItemHistory history = invocation.getArgument(0);
            history.setId(500L);
            return history;
        });
        when(lostClaims.findByItemId(1L)).thenReturn(List.of(claim));

        assertThat(archiver.archive(ItemKind.LOST, 1L)).isTrue();

        ArgumentCaptor<LostItemHistory> saved = ArgumentCaptor.forClass(LostItemHistory.class);
        InOrder order = inOrder(lostHistory, similarItems, lostClaims, lostItems);
        order.verify(lostHistory).save(saved.capture());
        order.verify(similarItems).deleteByLostItemId(1L);
        order.verify(lostClaims).saveAll(List.of(claim));
        order.verify(lostItems).delete(item);

        assertThat(saved.getValue().getTitle()).isEqualTo("Чорний рюкзак");
        assertThat(saved.getValue().getCurrency()).isEqualTo("UAH");
        assertThat(saved.getValue().getInfo()).isSameAs(item.getInfo());
        assertThat(saved.getValue().getArchivedAt()).isEqualTo(NOW);
        assertThat(claim.getItem()).as("the live link is cleared").isNull();
        assertThat(claim.getArchivedItem()).as("the claim follows the item into the archive").isSameAs(saved.getValue());
        assertThat(claim.getContactInfo()).as("the claimant's contacts stay").isNotNull();
    }

    @Test
    void doesNothingWhenTheItemIsAlreadyGone() {
        when(lostItems.findById(1L)).thenReturn(Optional.empty());

        assertThat(archiver.archive(ItemKind.LOST, 1L)).isFalse();

        verify(lostHistory, never()).save(any());
        verify(similarItems, never()).deleteByLostItemId(anyLong());
        verify(lostClaims, never()).saveAll(any());
    }
}
