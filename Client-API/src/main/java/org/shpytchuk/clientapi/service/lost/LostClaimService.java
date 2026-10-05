package org.shpytchuk.clientapi.service.lost;

import org.shpytchuk.clientapi.entity.lost.LostItem;
import org.shpytchuk.clientapi.entity.lost.LostItemClaim;
import org.shpytchuk.clientapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.clientapi.repository.lost.LostItemClaimRepository;
import org.shpytchuk.clientapi.repository.lost.LostItemRepository;
import org.shpytchuk.clientapi.service.ClaimService;
import org.shpytchuk.clientapi.service.payment.ClaimUnlockLimiter;
import org.springframework.stereotype.Service;

@Service
public class LostClaimService extends ClaimService<LostItem, LostItemClaim> {

    public LostClaimService(LostItemRepository itemRepository,
                            LostItemClaimRepository claimRepository,
                            ContactInfoRepository contactInfoRepository,
                            ClaimUnlockLimiter unlockLimiter) {
        super(itemRepository, claimRepository, contactInfoRepository, unlockLimiter,
                LostItemClaim::new, "LostItem");
    }
}
