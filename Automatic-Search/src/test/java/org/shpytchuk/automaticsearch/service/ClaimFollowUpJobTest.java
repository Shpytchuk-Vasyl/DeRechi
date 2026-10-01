package org.shpytchuk.automaticsearch.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.shpytchuk.automaticsearch.entity.LostItemClaim;
import org.shpytchuk.automaticsearch.repository.ContactInfoRepository;
import org.shpytchuk.automaticsearch.repository.FoundItemClaimRepository;
import org.shpytchuk.automaticsearch.repository.LostItemClaimRepository;
import org.springframework.amqp.AmqpConnectException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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

    private LostItemClaimRepository lostClaims;
    private ContactInfoRepository contactInfos;
    private ClaimNotifier notifier;
    private ItemArchiver archiver;
    private ClaimFollowUpJob job;

    @BeforeEach
    void setUp() {
        lostClaims = mock(LostItemClaimRepository.class);
        contactInfos = mock(ContactInfoRepository.class);
        notifier = mock(ClaimNotifier.class);
        archiver = mock(ItemArchiver.class);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ClaimRepositories repositories = new ClaimRepositories(lostClaims, mock(FoundItemClaimRepository.class));
        ClaimFollowUps followUps = new ClaimFollowUps(repositories, notifier, contactInfos, clock);
        job = new ClaimFollowUpJob(repositories, followUps, archiver, ClaimNotifierTest.PROPERTIES, clock);
    }

    @Test
    void remindsTheAuthorFirstAndStampsTheClaimAfterwards() {
        LostItemClaim claim = ClaimNotifierTest.lostClaim("+380671234567");
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
        LostItemClaim claim = ClaimNotifierTest.lostClaim("+380671234567");
        claim.setAuthorRemindedAt(NOW.minus(Duration.ofDays(2)));
        LostItemClaim next = ClaimNotifierTest.lostClaim("+380671234567");
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
        LostItemClaim claim = ClaimNotifierTest.lostClaim("+380671234567");
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
        LostItemClaim claim = ClaimNotifierTest.lostClaim("+380671234567");
        claim.getItem().setId(5L);
        when(lostClaims.findByItemNotNullAndConfirmedAtNotNull()).thenReturn(List.of(claim));

        job.run();

        verify(archiver).archive(ItemKind.LOST, 5L);
    }

    @Test
    void archivesOnlyItemsWhoseNewestClaimIsOlderThanTheWait() {
        Instant cutoff = NOW.minus(Duration.ofDays(7));
        LostItemClaim quiet = ClaimNotifierTest.lostClaim("+380671234567");
        quiet.getItem().setId(1L);
        LostItemClaim busy = ClaimNotifierTest.lostClaim("+380671234567");
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
    void deletesExpiredClaimsWithTheirContacts() {
        LostItemClaim claim = ClaimNotifierTest.lostClaim("+380671234567");
        claim.getContactInfo().setId(77L);
        when(lostClaims.findByCreatedAtLessThanEqualOrderByIdAsc(NOW.minus(Duration.ofDays(365)))).thenReturn(List.of(claim));
        when(lostClaims.findById(42L)).thenReturn(Optional.of(claim));

        job.run();

        InOrder order = inOrder(lostClaims, contactInfos);
        order.verify(lostClaims).deleteAllByIdInBatch(List.of(42L));
        order.verify(contactInfos).deleteAllByIdInBatch(List.of(77L));
        verify(archiver, never()).archive(any(), anyLong());
    }
}
