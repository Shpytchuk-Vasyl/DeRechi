package org.shpytchuk.clientapi.service.payment;

import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.client.FourthwallClient;
import org.shpytchuk.clientapi.client.FourthwallProduct;
import org.shpytchuk.clientapi.config.FourthwallProperties;
import org.shpytchuk.clientapi.dto.ClaimDto;
import org.shpytchuk.clientapi.exeption.NotFoundException;
import org.shpytchuk.clientapi.service.ClaimService;
import org.shpytchuk.clientapi.service.found.FoundClaimService;
import org.shpytchuk.clientapi.service.lost.LostClaimService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ClaimUnlockService {

    private static final Logger log = LoggerFactory.getLogger(ClaimUnlockService.class);

    private final LostClaimService lostClaimService;
    private final FoundClaimService foundClaimService;
    private final FourthwallClient fourthwall;
    private final FourthwallProperties properties;

    public ClaimDto unlockLost(Long itemId, Long claimId) {
        return unlock(lostClaimService, itemId, claimId, "lost");
    }

    public ClaimDto unlockFound(Long itemId, Long claimId) {
        return unlock(foundClaimService, itemId, claimId, "found");
    }

    private ClaimDto unlock(ClaimService<?, ?> service, Long itemId, Long claimId, String kind) {
        ClaimDto claim = service.find(itemId, claimId)
                .orElseThrow(() -> new NotFoundException(service.claimEntityName(), claimId));
        if (claim.paid() || claim.paymentVariantId() != null) {
            return claim;
        }
        service.reserveCheckout(claim.id());
        FourthwallProduct product;
        try {
            product = fourthwall.createDigitalProduct(
                    properties.productName().formatted(kind + "-" + claim.id()),
                    properties.productDescription(),
                    properties.price());
        } catch (RuntimeException e) {
            service.releaseCheckout(claim.id());
            throw e;
        }
        ClaimDto attached = service.attachProduct(claim.id(), product);
        if (product.variantId().equals(attached.paymentVariantId())) {
            log.info("Checkout created for {} claim {}: Fourthwall product {}, variant {}",
                    kind, claim.id(), product.productId(), product.variantId());
        } else {
            log.warn("Fourthwall product {} is orphaned: {} claim {} already had variant {}",
                    product.productId(), kind, claim.id(), attached.paymentVariantId());
        }
        return attached;
    }
}
