package org.shpytchuk.automaticsearch.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.automaticsearch.entity.Claim;
import org.shpytchuk.automaticsearch.repository.ClaimRepository;
import org.shpytchuk.automaticsearch.repository.FoundItemClaimRepository;
import org.shpytchuk.automaticsearch.repository.LostItemClaimRepository;
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
