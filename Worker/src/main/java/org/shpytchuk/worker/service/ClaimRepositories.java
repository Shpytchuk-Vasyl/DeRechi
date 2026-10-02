package org.shpytchuk.worker.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.worker.entity.matching.Claim;
import org.shpytchuk.worker.repository.ClaimRepository;
import org.shpytchuk.worker.repository.found.FoundItemClaimRepository;
import org.shpytchuk.worker.repository.lost.LostItemClaimRepository;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class ClaimRepositories {

    private final LostItemClaimRepository lostItemClaimRepository;
    private final FoundItemClaimRepository foundItemClaimRepository;

    public ClaimRepository<? extends Claim> of(ItemKind kind) {
        return switch (kind) {
            case LOST -> lostItemClaimRepository;
            case FOUND -> foundItemClaimRepository;
        };
    }
}
