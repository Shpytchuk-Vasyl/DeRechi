package org.shpytchuk.worker.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.worker.config.ItemSearchConfig;
import org.shpytchuk.worker.entity.detail.Place;
import org.shpytchuk.worker.entity.found.FoundItem;
import org.shpytchuk.worker.entity.lost.LostItem;
import org.shpytchuk.worker.entity.thing.ThingCategory;
import org.shpytchuk.worker.event.ItemCreatedEvent;
import org.shpytchuk.worker.event.ItemUpdatedEvent;
import org.shpytchuk.worker.language.LanguageResolver;
import org.shpytchuk.worker.repository.AbstractRepositoryTests;
import org.shpytchuk.worker.service.ItemKinds;
import org.shpytchuk.worker.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Both handler beans on the real schema: the query, the rank, the {@code INSERT ... unnest} into similar_item and,
 * for an edited notice, the delete of the candidates nobody was notified about.
 */
@Import({ItemSearchConfig.class, ItemKinds.class, LanguageResolver.class})
class ItemMatchHandlerTests extends AbstractRepositoryTests {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 10);
    private static final double LAT = 49.8419;
    private static final double LON = 24.0315;

    @Autowired
    private ItemMatchHandler<FoundItem> lostItemCreated;

    @Autowired
    private ItemMatchHandler<LostItem> foundItemCreated;

    private ThingCategory wallets;
    private Place rynok;

    @BeforeEach
    void setUp() {
        wallets = category("WALLET");
        rynok = place("ChIJrynok", LAT, LON);
    }

    @Test
    void storesTheFoundCandidatesOfANewLostItemWithTheirRank() {
        FoundItem sameWallet = item(new FoundItem(), "Black leather wallet", null, DATE, wallets, rynok);
        FoundItem somePurse = item(new FoundItem(), "Purse", "black leather", DATE, wallets, rynok);
        item(new FoundItem(), "Black leather wallet", null, DATE, category("KEYS"), rynok);
        LostItem lost = item(new LostItem(), "Black leather wallet", null, DATE, wallets, rynok);

        lostItemCreated.onItemCreated(event(lost.getId(), "Black leather wallet"));

        List<Map<String, Object>> rows = similarItems();
        assertThat(rows).extracting(row -> row.get("found_item_id"))
                .containsExactly(sameWallet.getId(), somePurse.getId());
        assertThat(rows).extracting(row -> row.get("lost_item_id")).containsOnly(lost.getId());
        assertThat((Double) rows.getFirst().get("match_order")).isGreaterThan((Double) rows.getLast().get("match_order"));
    }

    @Test
    void storesTheLostCandidatesOfANewFoundItem() {
        LostItem dayBefore = item(new LostItem(), "Black wallet", null, DATE.minusDays(1), wallets, rynok);
        LostItem dayAfter = item(new LostItem(), "Black wallet", null, DATE.plusDays(1), wallets, rynok);
        item(new LostItem(), "Wallet", null, DATE, wallets, rynok);
        FoundItem found = item(new FoundItem(), "Black wallet", null, DATE, wallets, rynok);

        foundItemCreated.onItemCreated(event(found.getId(), "Black wallet"));

        List<Map<String, Object>> rows = similarItems();
        assertThat(rows).extracting(row -> row.get("lost_item_id"))
                .containsExactlyInAnyOrder(dayBefore.getId(), dayAfter.getId());
        assertThat(rows).extracting(row -> row.get("found_item_id")).containsOnly(found.getId());
    }

    @Test
    void storesNothingWhenNoNoticeOfTheOtherKindIsClose() {
        item(new FoundItem(), "Black wallet", null, DATE.plusDays(10), wallets, rynok);
        LostItem lost = item(new LostItem(), "Black wallet", null, DATE, wallets, rynok);

        lostItemCreated.onItemCreated(event(lost.getId(), "Black wallet"));

        assertThat(similarItems()).isEmpty();
    }

    @Test
    void handlesARedeliveredEventWithoutDuplicatingTheCandidates() {
        item(new FoundItem(), "Black wallet", null, DATE, wallets, rynok);
        LostItem lost = item(new LostItem(), "Black wallet", null, DATE, wallets, rynok);
        ItemCreatedEvent event = event(lost.getId(), "Black wallet");

        lostItemCreated.onItemCreated(event);
        lostItemCreated.onItemCreated(event);

        assertThat(similarItems()).hasSize(1);
    }

    @Test
    void anEditedLostItemDropsTheCandidatesNobodyWasNotifiedAboutAndKeepsTheRest() {
        FoundItem stale = item(new FoundItem(), "Black wallet", null, DATE, wallets, rynok);
        FoundItem notified = item(new FoundItem(), "Black wallet", "leather", DATE, wallets, rynok);
        FoundItem fresh = item(new FoundItem(), "Blue purse", null, DATE, wallets, rynok);
        LostItem lost = item(new LostItem(), "Black wallet", null, DATE, wallets, rynok);
        lostItemCreated.onItemCreated(event(lost.getId(), "Black wallet"));
        assertThat(similarItems()).extracting(row -> row.get("found_item_id"))
                .containsExactlyInAnyOrder(stale.getId(), notified.getId());
        jdbc.update("UPDATE similar_item SET notified_at = now(), notified_by = 'admin@derechi.local' "
                + "WHERE found_item_id = ? AND lost_item_id = ?", notified.getId(), lost.getId());

        lostItemCreated.onItemUpdated(Fixtures.updated(lost.getId(), "Blue purse", DATE, wallets.getId(), LAT, LON));

        assertThat(similarItems()).extracting(row -> row.get("found_item_id"))
                .containsExactlyInAnyOrder(notified.getId(), fresh.getId());
        assertThat(jdbc.queryForObject("SELECT notified_by FROM similar_item WHERE found_item_id = ?", String.class,
                notified.getId())).isEqualTo("admin@derechi.local");
    }

    @Test
    void anEditedFoundItemSearchesTheLostSideAgain() {
        LostItem wallet = item(new LostItem(), "Black wallet", null, DATE, wallets, rynok);
        LostItem purse = item(new LostItem(), "Blue purse", null, DATE, wallets, rynok);
        FoundItem found = item(new FoundItem(), "Black wallet", null, DATE, wallets, rynok);
        foundItemCreated.onItemCreated(event(found.getId(), "Black wallet"));
        assertThat(similarItems()).extracting(row -> row.get("lost_item_id")).containsExactly(wallet.getId());

        foundItemCreated.onItemUpdated(Fixtures.updated(found.getId(), "Blue purse", DATE, wallets.getId(), LAT, LON));

        assertThat(similarItems()).extracting(row -> row.get("lost_item_id")).containsExactly(purse.getId());
        assertThat(similarItems()).extracting(row -> row.get("found_item_id")).containsOnly(found.getId());
    }

    @Test
    void aRedeliveredUpdateLeavesTheCandidatesAsTheyAre() {
        item(new FoundItem(), "Black wallet", null, DATE, wallets, rynok);
        LostItem lost = item(new LostItem(), "Black wallet", null, DATE, wallets, rynok);
        ItemUpdatedEvent event = Fixtures.updated(lost.getId(), "Black wallet", DATE, wallets.getId(), LAT, LON);

        lostItemCreated.onItemUpdated(event);
        lostItemCreated.onItemUpdated(event);

        assertThat(similarItems()).hasSize(1);
    }

    private List<Map<String, Object>> similarItems() {
        return jdbc.queryForList("SELECT found_item_id, lost_item_id, match_order FROM similar_item ORDER BY match_order DESC");
    }

    private ItemCreatedEvent event(Long id, String title) {
        return Fixtures.created(id, title, DATE, wallets.getId(), LAT, LON);
    }
}
