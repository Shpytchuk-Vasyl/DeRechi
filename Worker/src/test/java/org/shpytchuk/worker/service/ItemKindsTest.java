package org.shpytchuk.worker.service;

import org.junit.jupiter.api.Test;
import org.shpytchuk.worker.repository.found.FoundItemClaimRepository;
import org.shpytchuk.worker.repository.lost.LostItemClaimRepository;
import org.shpytchuk.worker.support.Kinds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ItemKindsTest {

    private final LostItemClaimRepository lostClaims = mock(LostItemClaimRepository.class);
    private final FoundItemClaimRepository foundClaims = mock(FoundItemClaimRepository.class);
    private final ItemKinds kinds = Kinds.of(lostClaims, foundClaims);

    @Test
    void answersEveryKindWithItsOwnOps() {
        for (ItemKind kind : ItemKind.values()) {
            assertThat(kinds.of(kind).kind()).isEqualTo(kind);
        }
    }

    @Test
    void handsOutTheClaimRepositoryOfTheKind() {
        assertThat(kinds.claims(ItemKind.LOST)).isSameAs(lostClaims);
        assertThat(kinds.claims(ItemKind.FOUND)).isSameAs(foundClaims);
    }

    @Test
    void theKindsAreEachOthersOpposite() {
        assertThat(ItemKind.LOST.opposite()).isEqualTo(ItemKind.FOUND);
        assertThat(ItemKind.FOUND.opposite()).isEqualTo(ItemKind.LOST);
    }
}
