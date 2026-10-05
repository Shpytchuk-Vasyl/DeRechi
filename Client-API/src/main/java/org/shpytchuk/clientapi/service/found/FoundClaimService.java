package org.shpytchuk.clientapi.service.found;

import org.shpytchuk.clientapi.entity.found.FoundItem;
import org.shpytchuk.clientapi.entity.found.FoundItemClaim;
import org.shpytchuk.clientapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.clientapi.repository.found.FoundItemClaimRepository;
import org.shpytchuk.clientapi.repository.found.FoundItemRepository;
import org.shpytchuk.clientapi.service.ClaimService;
import org.shpytchuk.clientapi.service.payment.ClaimUnlockLimiter;
import org.springframework.stereotype.Service;

@Service
public class FoundClaimService extends ClaimService<FoundItem, FoundItemClaim> {

    public FoundClaimService(FoundItemRepository itemRepository,
                             FoundItemClaimRepository claimRepository,
                             ContactInfoRepository contactInfoRepository,
                             ClaimUnlockLimiter unlockLimiter) {
        super(itemRepository, claimRepository, contactInfoRepository, unlockLimiter,
                FoundItemClaim::new, "FoundItem");
    }
}
