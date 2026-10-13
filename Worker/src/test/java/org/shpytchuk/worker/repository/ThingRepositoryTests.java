package org.shpytchuk.worker.repository;

import org.junit.jupiter.api.Test;
import org.shpytchuk.worker.entity.detail.Place;
import org.shpytchuk.worker.entity.found.FoundItem;
import org.shpytchuk.worker.entity.lost.LostItem;
import org.shpytchuk.worker.entity.thing.ThingCategory;
import org.shpytchuk.worker.repository.found.FoundItemRepository;
import org.shpytchuk.worker.repository.lost.LostItemRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ThingRepositoryTests extends AbstractRepositoryTests {

    private static final LocalDate CUTOFF = LocalDate.of(2026, 9, 1);

    @Autowired
    private LostItemRepository lostItems;

    @Autowired
    private FoundItemRepository foundItems;

    @Test
    void picksItemsDatedStrictlyBeforeTheCutoff() {
        ThingCategory wallets = category("WALLET");
        Place rynok = place("ChIJrynok", 49.8419, 24.0315);
        LostItem older = item(new LostItem(), "Wallet", null, CUTOFF.minusDays(10), wallets, rynok);
        LostItem dayBefore = item(new LostItem(), "Wallet", null, CUTOFF.minusDays(1), wallets, rynok);
        item(new LostItem(), "Wallet", null, CUTOFF, wallets, rynok);
        item(new LostItem(), "Wallet", null, CUTOFF.plusDays(1), wallets, rynok);
        FoundItem found = item(new FoundItem(), "Wallet", null, CUTOFF.minusDays(1), wallets, rynok);
        item(new FoundItem(), "Wallet", null, CUTOFF, wallets, rynok);

        assertThat(lostItems.findIdsDatedBefore(CUTOFF)).containsExactly(older.getId(), dayBefore.getId());
        assertThat(foundItems.findIdsDatedBefore(CUTOFF)).containsExactly(found.getId());
    }
}
