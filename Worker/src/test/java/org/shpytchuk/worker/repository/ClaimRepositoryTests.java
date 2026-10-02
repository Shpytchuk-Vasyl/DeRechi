package org.shpytchuk.worker.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.worker.entity.found.FoundItem;
import org.shpytchuk.worker.entity.found.FoundItemClaim;
import org.shpytchuk.worker.entity.lost.LostItem;
import org.shpytchuk.worker.entity.lost.LostItemClaim;
import org.shpytchuk.worker.entity.lost.LostItemHistory;
import org.shpytchuk.worker.entity.matching.Claim;
import org.shpytchuk.worker.entity.thing.ThingCategory;
import org.shpytchuk.worker.entity.detail.Place;
import org.shpytchuk.worker.repository.found.FoundItemClaimRepository;
import org.shpytchuk.worker.repository.lost.LostItemClaimRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** The derived queries {@code ClaimFollowUpJob} selects its work with, on the real schema. */
class ClaimRepositoryTests extends AbstractRepositoryTests {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant CUTOFF = NOW.minus(Duration.ofDays(1));

    @Autowired
    private LostItemClaimRepository lostClaims;

    @Autowired
    private FoundItemClaimRepository foundClaims;

    private ThingCategory wallets;
    private Place rynok;
    private LostItem lost;

    @BeforeEach
    void setUp() {
        wallets = category("WALLET");
        rynok = place("ChIJrynok", 49.8419, 24.0315);
        lost = lostItem();
    }

    @Test
    void picksLiveUnconfirmedClaimsOldEnoughForTheAuthorReminder() {
        LostItemClaim due = claim(lost, "+380501111111", CUTOFF.minusSeconds(60));
        LostItemClaim exactlyAtTheCutoff = claim(lost, "+380502222222", CUTOFF);
        claim(lost, "+380503333333", CUTOFF.plusSeconds(60));
        stamp(claim(lost, "+380504444444", CUTOFF.minusSeconds(60)), claim -> claim.setAuthorRemindedAt(NOW));
        stamp(claim(lost, "+380505555555", CUTOFF.minusSeconds(60)), claim -> claim.setConfirmedAt(NOW));
        archived(claim(lostItem(), "+380506666666", CUTOFF.minusSeconds(60)));

        assertThat(ids(lostClaims.findByItemNotNullAndConfirmedAtNullAndAuthorRemindedAtNullAndCreatedAtLessThanEqualOrderByIdAsc(CUTOFF)))
                .containsExactly(due.getId(), exactlyAtTheCutoff.getId());
    }

    @Test
    void picksClaimsWhoseAuthorWasRemindedLongEnoughAgoForTheClaimantReminder() {
        LostItemClaim due = stamp(claim(lost, "+380501111111", NOW.minus(Duration.ofDays(3))),
                claim -> claim.setAuthorRemindedAt(CUTOFF.minusSeconds(60)));
        stamp(claim(lost, "+380502222222", NOW.minus(Duration.ofDays(3))),
                claim -> claim.setAuthorRemindedAt(CUTOFF.plusSeconds(60)));
        claim(lost, "+380503333333", NOW.minus(Duration.ofDays(3)));
        stamp(claim(lost, "+380504444444", NOW.minus(Duration.ofDays(3))), claim -> {
            claim.setAuthorRemindedAt(CUTOFF.minusSeconds(60));
            claim.setClaimantRemindedAt(NOW);
        });
        stamp(claim(lost, "+380505555555", NOW.minus(Duration.ofDays(3))), claim -> {
            claim.setAuthorRemindedAt(CUTOFF.minusSeconds(60));
            claim.setConfirmedAt(NOW);
        });

        assertThat(ids(lostClaims.findByItemNotNullAndConfirmedAtNullAndClaimantRemindedAtNullAndAuthorRemindedAtLessThanEqualOrderByIdAsc(CUTOFF)))
                .containsExactly(due.getId());
    }

    @Test
    void picksConfirmedClaimsOnlyWhileTheNoticeIsStillLive() {
        LostItemClaim confirmed = stamp(claim(lost, "+380501111111", NOW), claim -> claim.setConfirmedAt(NOW));
        claim(lost, "+380502222222", NOW);
        LostItemClaim confirmedAndArchived = stamp(claim(lostItem(), "+380503333333", NOW), claim -> claim.setConfirmedAt(NOW));
        archived(confirmedAndArchived);

        assertThat(ids(lostClaims.findByItemNotNullAndConfirmedAtNotNull())).containsExactly(confirmed.getId());
    }

    @Test
    void tellsAQuietNoticeFromOneWithARecentClaim() {
        LostItem quiet = lost;
        LostItem busy = lostItem();
        claim(quiet, "+380501111111", CUTOFF.minus(Duration.ofDays(2)));
        claim(busy, "+380502222222", CUTOFF.minus(Duration.ofDays(2)));
        claim(busy, "+380503333333", CUTOFF.plusSeconds(60));

        assertThat(lostClaims.findByItemNotNullAndCreatedAtLessThanEqual(CUTOFF))
                .extracting(claim -> claim.getItem().getId())
                .containsExactlyInAnyOrder(quiet.getId(), busy.getId());
        assertThat(lostClaims.existsByItemIdAndCreatedAtGreaterThan(quiet.getId(), CUTOFF)).isFalse();
        assertThat(lostClaims.existsByItemIdAndCreatedAtGreaterThan(busy.getId(), CUTOFF)).isTrue();
    }

    @Test
    void picksExpiredClaimsWhetherTheNoticeIsLiveOrArchived() {
        Instant retention = NOW.minus(Duration.ofDays(365));
        LostItemClaim live = claim(lost, "+380501111111", retention.minusSeconds(60));
        LostItemClaim archived = archived(claim(lostItem(), "+380502222222", retention.minusSeconds(60)));
        claim(lost, "+380503333333", retention.plusSeconds(60));

        assertThat(ids(lostClaims.findByCreatedAtLessThanEqualOrderByIdAsc(retention)))
                .containsExactly(live.getId(), archived.getId());
    }

    @Test
    void findsTheClaimsOfANoticeWithTheirContacts() {
        LostItemClaim first = claim(lost, "+380501111111", NOW);
        LostItemClaim second = claim(lost, "+380502222222", NOW);
        claim(lostItem(), "+380503333333", NOW);
        entityManager.clear();

        List<LostItemClaim> claims = lostClaims.findByItemId(lost.getId());
        entityManager.clear();

        assertThat(ids(claims)).containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(claims).extracting(claim -> claim.getContactInfo().getPhone())
                .containsExactlyInAnyOrder("+380501111111", "+380502222222");
    }

    @Test
    void loadsAClaimWithTheClaimantAndTheAuthorContactsForTheNotifier() {
        FoundItem found = item(new FoundItem(), "Wallet", null, LocalDate.of(2026, 9, 10), wallets, rynok);
        FoundItemClaim claim = claim(new FoundItemClaim(), found, "+380501111111", NOW);
        entityManager.clear();

        FoundItemClaim loaded = foundClaims.findWithDetailsById(claim.getId()).orElseThrow();
        entityManager.clear();

        assertThatCode(() -> {
            assertThat(loaded.getContactInfo().getPhone()).isEqualTo("+380501111111");
            assertThat(loaded.getItem().getTitle()).isEqualTo("Wallet");
            assertThat(loaded.getItem().getInfo().getEmail()).isEqualTo("author@example.com");
        }).as("everything the notifier reads is fetched up front").doesNotThrowAnyException();
    }

    private LostItem lostItem() {
        return item(new LostItem(), "Wallet", null, LocalDate.of(2026, 9, 10), wallets, rynok);
    }

    private LostItemClaim claim(LostItem item, String phone, Instant createdAt) {
        return claim(new LostItemClaim(), item, phone, createdAt);
    }

    private <C extends Claim> C stamp(C claim, Consumer<C> change) {
        change.accept(claim);
        entityManager.flush();
        return claim;
    }

    private LostItemClaim archived(LostItemClaim claim) {
        LostItemHistory history = new LostItemHistory();
        history.setArchivedAt(NOW);
        item(history, "Wallet", null, LocalDate.of(2026, 9, 10), wallets, rynok);
        claim.moveToArchive(history);
        entityManager.flush();
        return claim;
    }

    private static List<Long> ids(List<? extends Claim> claims) {
        return claims.stream().map(Claim::getId).toList();
    }
}
