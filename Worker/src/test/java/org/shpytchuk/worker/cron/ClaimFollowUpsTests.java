package org.shpytchuk.worker.cron;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.worker.entity.detail.Place;
import org.shpytchuk.worker.entity.lost.LostItem;
import org.shpytchuk.worker.entity.lost.LostItemClaim;
import org.shpytchuk.worker.entity.lost.LostItemHistory;
import org.shpytchuk.worker.entity.thing.ThingCategory;
import org.shpytchuk.worker.repository.AbstractRepositoryTests;
import org.shpytchuk.worker.service.ClaimNotifier;
import org.shpytchuk.worker.service.ClaimRepositories;
import org.shpytchuk.worker.service.ItemKind;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/** The per-claim transactions of the follow-up job against the real foreign keys. */
@Import({ClaimFollowUps.class, ClaimRepositories.class, ClaimFollowUpsTests.FixedClock.class})
class ClaimFollowUpsTests extends AbstractRepositoryTests {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Autowired
    private ClaimFollowUps followUps;

    @MockitoBean
    private ClaimNotifier notifier;

    private ThingCategory wallets;
    private Place rynok;

    @BeforeEach
    void setUp() {
        wallets = category("WALLET");
        rynok = place("ChIJrynok", 49.8419, 24.0315);
    }

    @Test
    void purgeDeletesTheClaimAndTheClaimantContactsButKeepsTheNotice() {
        LostItem item = lostItem();
        LostItemClaim expired = claim(new LostItemClaim(), item, "+380501111111", NOW);
        LostItemClaim other = claim(new LostItemClaim(), item, "+380502222222", NOW);
        Long claimantContact = expired.getContactInfo().getId();

        assertThat(followUps.purge(ItemKind.LOST, expired.getId())).isTrue();
        entityManager.flush();
        entityManager.clear();

        assertThat(count("lost_item_claim")).isEqualTo(1);
        assertThat(entityManager.find(LostItemClaim.class, other.getId())).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contact_info WHERE id = ?", Long.class, claimantContact))
                .isZero();
        assertThat(entityManager.find(LostItem.class, item.getId()).getInfo().getEmail())
                .as("the author's contacts are not the claimant's").isEqualTo("author@example.com");
    }

    @Test
    void purgeDeletesAClaimThatAlreadyFollowedItsNoticeIntoTheArchive() {
        LostItemClaim claim = claim(new LostItemClaim(), lostItem(), "+380501111111", NOW);
        LostItemHistory history = new LostItemHistory();
        history.setArchivedAt(NOW);
        claim.moveToArchive(item(history, "Wallet", null, LocalDate.of(2026, 9, 10), wallets, rynok));
        entityManager.flush();

        assertThat(followUps.purge(ItemKind.LOST, claim.getId())).isTrue();
        entityManager.flush();

        assertThat(count("lost_item_claim")).isZero();
        assertThat(count("lost_item_history")).isEqualTo(1);
    }

    @Test
    void purgeOfAClaimThatIsAlreadyGoneIsANoOp() {
        assertThat(followUps.purge(ItemKind.LOST, 404L)).isFalse();
    }

    @Test
    void remindingTheAuthorStampsTheClaimInTheDatabase() {
        LostItemClaim claim = claim(new LostItemClaim(), lostItem(), "+380501111111", NOW.minusSeconds(86_400 * 2));
        entityManager.clear();

        assertThat(followUps.remindAuthor(ItemKind.LOST, claim.getId())).isTrue();
        entityManager.flush();
        entityManager.clear();

        verify(notifier).remindAuthor(any(), any());
        assertThat(entityManager.find(LostItemClaim.class, claim.getId()).getAuthorRemindedAt()).isEqualTo(NOW);
        assertThat(followUps.remindAuthor(ItemKind.LOST, claim.getId())).as("only once").isFalse();
    }

    private LostItem lostItem() {
        return item(new LostItem(), "Wallet", null, LocalDate.of(2026, 9, 10), wallets, rynok);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
