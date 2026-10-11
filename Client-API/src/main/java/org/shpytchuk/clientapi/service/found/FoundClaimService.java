package org.shpytchuk.clientapi.service.found;

import org.shpytchuk.clientapi.entity.ItemKind;
import org.shpytchuk.clientapi.entity.found.FoundItem;
import org.shpytchuk.clientapi.entity.found.FoundItemClaim;
import org.shpytchuk.clientapi.entity.payment.FourthwallOrder;
import org.shpytchuk.clientapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.clientapi.repository.found.FoundItemClaimRepository;
import org.shpytchuk.clientapi.repository.found.FoundItemRepository;
import org.shpytchuk.clientapi.service.ClaimService;
import org.shpytchuk.clientapi.service.payment.ClaimUnlockLimiter;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class FoundClaimService extends ClaimService<FoundItem, FoundItemClaim> {

    public FoundClaimService(FoundItemRepository itemRepository,
                             FoundItemClaimRepository claimRepository,
                             ContactInfoRepository contactInfoRepository,
                             ClaimUnlockLimiter unlockLimiter) {
        super(itemRepository, claimRepository, contactInfoRepository, unlockLimiter,
                FoundItemClaim::new, ItemKind.FOUND);
    }

    @Override
    public void link(FourthwallOrder order, FoundItemClaim claim) {
        order.setFoundItemClaim(claim);
    }

    @Override
    public Optional<FoundItemClaim> claimOf(FourthwallOrder order) {
        return Optional.ofNullable(order.getFoundItemClaim());
    }
}
