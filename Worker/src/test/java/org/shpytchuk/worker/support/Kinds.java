package org.shpytchuk.worker.support;

import org.shpytchuk.worker.repository.SimilarItemRepository;
import org.shpytchuk.worker.repository.found.FoundItemClaimRepository;
import org.shpytchuk.worker.repository.found.FoundItemHistoryRepository;
import org.shpytchuk.worker.repository.found.FoundItemRepository;
import org.shpytchuk.worker.repository.lost.LostItemClaimRepository;
import org.shpytchuk.worker.repository.lost.LostItemHistoryRepository;
import org.shpytchuk.worker.repository.lost.LostItemRepository;
import org.shpytchuk.worker.service.ItemKinds;

import static org.mockito.Mockito.mock;

public final class Kinds {

    private Kinds() {
    }

    public static ItemKinds of(LostItemClaimRepository lostClaims, FoundItemClaimRepository foundClaims) {
        return new ItemKinds(mock(LostItemRepository.class), mock(LostItemHistoryRepository.class), lostClaims,
                mock(FoundItemRepository.class), mock(FoundItemHistoryRepository.class), foundClaims,
                mock(SimilarItemRepository.class));
    }

    public static ItemKinds lost(LostItemRepository items, LostItemHistoryRepository histories,
                                 LostItemClaimRepository claims, SimilarItemRepository matches) {
        return new ItemKinds(items, histories, claims,
                mock(FoundItemRepository.class), mock(FoundItemHistoryRepository.class), mock(FoundItemClaimRepository.class),
                matches);
    }
}
