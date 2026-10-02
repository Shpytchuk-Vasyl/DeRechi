package org.shpytchuk.clientapi.service.lost;

import org.shpytchuk.clientapi.entity.lost.LostItem;
import org.shpytchuk.clientapi.entity.lost.LostItemClaim;
import org.shpytchuk.clientapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.clientapi.repository.lost.LostItemClaimRepository;
import org.shpytchuk.clientapi.repository.lost.LostItemRepository;
import org.shpytchuk.clientapi.service.ClaimService;
import org.springframework.stereotype.Service;

@Service
public class LostClaimService extends ClaimService<LostItem, LostItemClaim> {

    public LostClaimService(LostItemRepository itemRepository,
                            LostItemClaimRepository claimRepository,
                            ContactInfoRepository contactInfoRepository) {
        super(itemRepository, claimRepository, contactInfoRepository, LostItemClaim::new, "LostItem");
    }
}
