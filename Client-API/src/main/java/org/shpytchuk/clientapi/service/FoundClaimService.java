package org.shpytchuk.clientapi.service;

import org.shpytchuk.clientapi.entity.FoundItem;
import org.shpytchuk.clientapi.entity.FoundItemClaim;
import org.shpytchuk.clientapi.repository.ContactInfoRepository;
import org.shpytchuk.clientapi.repository.FoundItemClaimRepository;
import org.shpytchuk.clientapi.repository.FoundItemRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class FoundClaimService extends ClaimService<FoundItem, FoundItemClaim> {

    public FoundClaimService(FoundItemRepository itemRepository,
                             FoundItemClaimRepository claimRepository,
                             ContactInfoRepository contactInfoRepository,
                             ApplicationEventPublisher events) {
        super(itemRepository, claimRepository, contactInfoRepository, events,
                FoundItemClaim::new, "FoundItem", "item.found");
    }
}
