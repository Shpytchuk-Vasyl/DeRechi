package org.shpytchuk.clientapi.service;

import org.shpytchuk.clientapi.entity.LostItem;
import org.shpytchuk.clientapi.entity.LostItemClaim;
import org.shpytchuk.clientapi.repository.ContactInfoRepository;
import org.shpytchuk.clientapi.repository.LostItemClaimRepository;
import org.shpytchuk.clientapi.repository.LostItemRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class LostClaimService extends ClaimService<LostItem, LostItemClaim> {

    public LostClaimService(LostItemRepository itemRepository,
                            LostItemClaimRepository claimRepository,
                            ContactInfoRepository contactInfoRepository,
                            ApplicationEventPublisher events) {
        super(itemRepository, claimRepository, contactInfoRepository, events,
                LostItemClaim::new, "LostItem", "item.lost");
    }
}
