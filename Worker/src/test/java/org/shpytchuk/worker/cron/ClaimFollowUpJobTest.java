package org.shpytchuk.worker.cron;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.shpytchuk.worker.entity.lost.LostItemClaim;
import org.shpytchuk.worker.repository.ContactInfoRepository;
import org.shpytchuk.worker.repository.SimilarItemRepository;
import org.shpytchuk.worker.repository.lost.LostItemClaimRepository;
import org.shpytchuk.worker.repository.lost.LostItemHistoryRepository;
import org.shpytchuk.worker.repository.lost.LostItemRepository;
import org.shpytchuk.worker.service.ClaimNotifier;
import org.shpytchuk.worker.service.ItemArchiver;
import org.shpytchuk.worker.service.ItemKind;
import org.shpytchuk.worker.service.ItemKinds;
import org.shpytchuk.worker.support.Fixtures;
import org.shpytchuk.worker.support.Kinds;
import org.springframework.amqp.AmqpConnectException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimFollowUpJobTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    private LostItemRepository lostItems;
    private LostItemClaimRepository lostClaims;
    private ContactInfoRepository contactInfos;
    private ClaimNotifier notifier;
    private ItemArchiver archiver;
    private ClaimFollowUpJob job;

    @BeforeEach
    void setUp() {
        lostItems = mock(LostItemRepository.class);
        lostClaims = mock(LostItemClaimRepository.class);
        contactInfos = mock(ContactInfoRepository.class);
        notifier = mock(ClaimNotifier.class);
        archiver = mock(ItemArchiver.class);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ItemKinds kinds = Kinds.lost(lostItems, mock(LostItemHistoryRepository.class), lostClaims,
                mock(SimilarItemRepository.class));
        ClaimFollowUps followUps = new ClaimFollowUps(kinds, notifier, contactInfos, clock);
        job = new ClaimFollowUpJob(kinds, followUps, archiver, Fixtures.PROPERTIES, Fixtures.ARCHIVE, clock);
    }

    @Test
    void remindsTheAuthorFirstAndStampsTheClaimAfterwards() {
        LostItemClaim claim = Fixtures.lostClaim("+380671234567");
        when(lostClaims.findByItemNotNullAndConfirmedAtNullAndAuthorRemindedAtNullAndCreatedAtLessThanEqualOrderByIdAsc(
                NOW.minus(Duration.ofDays(1)))).thenReturn(List.of(claim));
        when(lostClaims.findWithDetailsById(42L)).thenReturn(Optional.of(claim));
        doAnswer(invocation -> {
            assertThat(claim.getAuthorRemindedAt()).as("stamped before the message went out").isNull();
            return null;
        }).when(notifier).remindAuthor(ItemKind.LOST, claim);

        job.run();

        InOrder order = inOrder(notifier, lostClaims);
        order.verify(notifier).remindAuthor(ItemKind.LOST, claim);
        order.verify(lostClaims).save(claim);
        assertThat(claim.getAuthorRemindedAt()).isEqualTo(NOW);
        assertThat(claim.getClaimantRemindedAt()).isNull();
    }

    @Test
    void leavesTheClaimDueWhenTheBrokerIsDown() {
        LostItemClaim claim = Fixtures.lostClaim("+380671234567");
        claim.setAuthorRemindedAt(NOW.minus(Duration.ofDays(2)));
        LostItemClaim next = Fixtures.lostClaim("+380671234567");
        next.setId(43L);
        when(lostClaims.findByItemNotNullAndConfirmedAtNullAndClaimantRemindedAtNullAndAuthorRemindedAtLessThanEqualOrderByIdAsc(
                NOW.minus(Duration.ofDays(1)))).thenReturn(List.of(claim, next));
        when(lostClaims.findWithDetailsById(42L)).thenReturn(Optional.of(claim));
        doThrow(new AmqpConnectException(new RuntimeException("connection refused")))
                .when(notifier).remindClaimant(ItemKind.LOST, claim);

        assertThatCode(job::run).doesNotThrowAnyException();

        assertThat(claim.getClaimantRemindedAt()).isNull();
        verify(lostClaims, never()).save(any());
        verify(lostClaims, never()).findWithDetailsById(43L);
    }

    @Test
    void doesNotRemindAboutAClaimConfirmedInTheMeantime() {
        LostItemClaim claim = Fixtures.lostClaim("+380671234567");
        claim.setConfirmedAt(NOW.minus(Duration.ofMinutes(1)));
        when(lostClaims.findByItemNotNullAndConfirmedAtNullAndAuthorRemindedAtNullAndCreatedAtLessThanEqualOrderByIdAsc(any()))
                .thenReturn(List.of(claim));
        when(lostClaims.findWithDetailsById(42L)).thenReturn(Optional.of(claim));

        job.run();

        verify(notifier, never()).remindAuthor(any(), any());
        assertThat(claim.getAuthorRemindedAt()).isNull();
    }

    @Test
    void archivesItemsWithAConfirmedClaim() {
        LostItemClaim claim = Fixtures.lostClaim("+380671234567");
        claim.getItem().setId(5L);
        when(lostClaims.findByItemNotNullAndConfirmedAtNotNull()).thenReturn(List.of(claim));

        job.run();

        verify(archiver).archive(ItemKind.LOST, 5L);
    }

    @Test
    void archivesOnlyItemsWhoseNewestClaimIsOlderThanTheWait() {
        Instant cutoff = NOW.minus(Duration.ofDays(7));
        LostItemClaim quiet = Fixtures.lostClaim("+380671234567");
        quiet.getItem().setId(1L);
        LostItemClaim busy = Fixtures.lostClaim("+380671234567");
        busy.setId(43L);
        busy.getItem().setId(2L);
        when(lostClaims.findByItemNotNullAndCreatedAtLessThanEqual(cutoff)).thenReturn(List.of(quiet, busy));
        when(lostClaims.existsByItemIdAndCreatedAtGreaterThan(1L, cutoff)).thenReturn(false);
        when(lostClaims.existsByItemIdAndCreatedAtGreaterThan(2L, cutoff)).thenReturn(true);

        job.run();

        verify(archiver).archive(ItemKind.LOST, 1L);
        verify(archiver, never()).archive(ItemKind.LOST, 2L);
    }

    @Test
    void archivesItemsDatedMoreThanTheMaxAgeAgoInUtc() {
        when(lostItems.findIdsDatedBefore(LocalDate.of(2026, 9, 1))).thenReturn(List.of(7L, 8L));

        job.run();

        verify(archiver).archive(ItemKind.LOST, 7L);
        verify(archiver).archive(ItemKind.LOST, 8L);
    }

    @Test
    void deletesExpiredClaimsWithTheirContacts() {
        LostItemClaim claim = Fixtures.lostClaim("+380671234567");
        claim.getContactInfo().setId(77L);
        when(lostClaims.findByCreatedAtLessThanEqualOrderByIdAsc(NOW.minus(Duration.ofDays(365)))).thenReturn(List.of(claim));
        when(lostClaims.findById(42L)).thenReturn(Optional.of(claim));

        job.run();

        InOrder order = inOrder(lostClaims, contactInfos);
        order.verify(lostClaims).deleteAllByIdInBatch(List.of(42L));
        order.verify(contactInfos).deleteAllByIdInBatch(List.of(77L));
        verify(archiver, never()).archive(any(), anyLong());
    }

    @Test
    void runsTheStepsInOrderAndSelectsEachOneAfterThePreviousStepFinished() {
        LostItemClaim reminded = Fixtures.lostClaim("+380671234567");
        LostItemClaim confirmed = Fixtures.lostClaim("+380671234567");
        confirmed.setId(43L);
        confirmed.getItem().setId(5L);
        when(lostClaims.findByItemNotNullAndConfirmedAtNullAndAuthorRemindedAtNullAndCreatedAtLessThanEqualOrderByIdAsc(any()))
                .thenReturn(List.of(reminded));
        when(lostClaims.findWithDetailsById(42L)).thenReturn(Optional.of(reminded));
        when(lostClaims.findByItemNotNullAndConfirmedAtNotNull()).thenReturn(List.of(confirmed));

        job.run();

        InOrder order = inOrder(lostClaims, lostItems, notifier, archiver);
        order.verify(lostClaims).findByItemNotNullAndConfirmedAtNullAndAuthorRemindedAtNullAndCreatedAtLessThanEqualOrderByIdAsc(any());
        order.verify(notifier).remindAuthor(ItemKind.LOST, reminded);
        order.verify(lostClaims).findByItemNotNullAndConfirmedAtNullAndClaimantRemindedAtNullAndAuthorRemindedAtLessThanEqualOrderByIdAsc(any());
        order.verify(lostClaims).findByItemNotNullAndConfirmedAtNotNull();
        order.verify(archiver).archive(ItemKind.LOST, 5L);
        order.verify(lostClaims).findByItemNotNullAndCreatedAtLessThanEqual(any());
        order.verify(lostItems).findIdsDatedBefore(any());
        order.verify(lostClaims).findByCreatedAtLessThanEqualOrderByIdAsc(any());
    }
}
