package org.shpytchuk.worker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.worker.entity.detail.Place;
import org.shpytchuk.worker.entity.found.FoundItem;
import org.shpytchuk.worker.entity.found.FoundItemClaim;
import org.shpytchuk.worker.entity.lost.LostItem;
import org.shpytchuk.worker.entity.lost.LostItemClaim;
import org.shpytchuk.worker.entity.thing.ThingCategory;
import org.shpytchuk.worker.repository.AbstractRepositoryTests;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Archiving against the real constraints: the claim CHECK (exactly one of item/archived item), the foreign
 * keys from similar_item and the claims, and the order in which Hibernate flushes the changes.
 * {@link ItemArchiverTest} covers the same flow with mocks.
 */
@Import({ItemArchiver.class, ClaimRepositories.class, ItemArchiverTests.FixedClock.class})
class ItemArchiverTests extends AbstractRepositoryTests {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2026, 9, 10);

    @Autowired
    private ItemArchiver archiver;

    private ThingCategory wallets;
    private Place rynok;

    @BeforeEach
    void setUp() {
        wallets = category("WALLET");
        rynok = place("ChIJrynok", 49.8419, 24.0315);
    }

    @Test
    void movesALostItemIntoHistoryWithItsClaimsAndDropsItsMatches() {
        LostItem lost = item(new LostItem(), "Black wallet", "with a photo inside", DATE, wallets, rynok);
        LostItem untouched = item(new LostItem(), "Keys", null, DATE, wallets, rynok);
        FoundItem candidate = item(new FoundItem(), "Wallet", null, DATE, wallets, rynok);
        match(candidate, lost);
        match(candidate, untouched);
        LostItemClaim claim = claim(new LostItemClaim(), lost, "+380501111111", NOW);
        Long claimantContact = claim.getContactInfo().getId();

        assertThat(archiver.archive(ItemKind.LOST, lost.getId())).isTrue();
        entityManager.flush();

        assertThat(count("lost_item WHERE id = " + lost.getId())).isZero();
        Map<String, Object> history = jdbc.queryForMap("SELECT * FROM lost_item_history");
        assertThat(history).containsEntry("title", "Black wallet")
                .containsEntry("description", "with a photo inside")
                .containsEntry("currency", "UAH")
                .containsEntry("info_id", lost.getInfo().getId())
                .containsEntry("archived_at", Timestamp.from(NOW));

        Map<String, Object> movedClaim = jdbc.queryForMap(
                "SELECT item_id, archived_item_id, contact_info_id FROM lost_item_claim WHERE id = ?", claim.getId());
        assertThat(movedClaim.get("item_id")).isNull();
        assertThat(movedClaim).containsEntry("archived_item_id", history.get("id"))
                .containsEntry("contact_info_id", claimantContact);

        assertThat(jdbc.queryForList("SELECT lost_item_id FROM similar_item", Long.class))
                .containsExactly(untouched.getId());
    }

    @Test
    void movesAFoundItemIntoHistoryWithItsClaimsAndDropsItsMatches() {
        FoundItem found = item(new FoundItem(), "Black wallet", null, DATE, wallets, rynok);
        LostItem candidate = item(new LostItem(), "Wallet", null, DATE, wallets, rynok);
        match(found, candidate);
        claim(new FoundItemClaim(), found, "+380501111111", NOW);
        claim(new FoundItemClaim(), found, "+380502222222", NOW);

        assertThat(archiver.archive(ItemKind.FOUND, found.getId())).isTrue();
        entityManager.flush();

        assertThat(count("found_item")).isZero();
        assertThat(count("found_item_history")).isEqualTo(1);
        assertThat(count("found_item_claim WHERE item_id IS NULL AND archived_item_id IS NOT NULL")).isEqualTo(2);
        assertThat(count("similar_item")).isZero();
        assertThat(count("lost_item")).as("the candidate itself stays").isEqualTo(1);
    }

    private void match(FoundItem found, LostItem lost) {
        jdbc.update("INSERT INTO similar_item (found_item_id, lost_item_id, match_order) VALUES (?, ?, 0.5)",
                found.getId(), lost.getId());
    }

    private long count(String tableAndCondition) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tableAndCondition, Long.class);
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
