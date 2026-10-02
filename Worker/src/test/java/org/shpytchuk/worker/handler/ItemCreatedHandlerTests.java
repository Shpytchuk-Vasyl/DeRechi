package org.shpytchuk.worker.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.worker.config.ItemSearchConfig;
import org.shpytchuk.worker.entity.detail.Place;
import org.shpytchuk.worker.entity.found.FoundItem;
import org.shpytchuk.worker.entity.lost.LostItem;
import org.shpytchuk.worker.entity.thing.ThingCategory;
import org.shpytchuk.worker.event.ItemCreatedEvent;
import org.shpytchuk.worker.language.LanguageResolver;
import org.shpytchuk.worker.repository.AbstractRepositoryTests;
import org.shpytchuk.worker.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Both handlers on the real schema: the query, the rank and the {@code INSERT ... unnest} into similar_item. */
@Import({ItemSearchConfig.class, LanguageResolver.class, LostItemCreatedHandler.class, FoundItemCreatedHandler.class})
class ItemCreatedHandlerTests extends AbstractRepositoryTests {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 10);
    private static final double LAT = 49.8419;
    private static final double LON = 24.0315;

    @Autowired
    private LostItemCreatedHandler lostItemCreated;

    @Autowired
    private FoundItemCreatedHandler foundItemCreated;

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
        LostItem first = item(new LostItem(), "Wallet", null, DATE.minusDays(1), wallets, rynok);
        LostItem second = item(new LostItem(), "Black wallet", null, DATE.plusDays(1), wallets, rynok);
        FoundItem found = item(new FoundItem(), "Black wallet", null, DATE, wallets, rynok);

        foundItemCreated.onItemCreated(event(found.getId(), "Black wallet"));

        List<Map<String, Object>> rows = similarItems();
        assertThat(rows).extracting(row -> row.get("lost_item_id"))
                .containsExactly(second.getId(), first.getId());
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

    private List<Map<String, Object>> similarItems() {
        return jdbc.queryForList("SELECT found_item_id, lost_item_id, match_order FROM similar_item ORDER BY match_order DESC");
    }

    private ItemCreatedEvent event(Long id, String title) {
        return Fixtures.created(id, title, DATE, wallets.getId(), LAT, LON);
    }
}
