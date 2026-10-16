package org.shpytchuk.clientapi.service.payment;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.clientapi.client.FourthwallClient;
import org.shpytchuk.clientapi.client.FourthwallProduct;
import org.shpytchuk.clientapi.config.FourthwallProperties;
import org.shpytchuk.clientapi.dto.ClaimDto;
import org.shpytchuk.clientapi.entity.ItemKind;
import org.shpytchuk.clientapi.exeption.NotFoundException;
import org.shpytchuk.clientapi.service.ClaimService;
import org.shpytchuk.clientapi.service.ClaimServices;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class ClaimUnlockService {

    private final ClaimServices claimServices;
    private final FourthwallClient fourthwall;
    private final FourthwallProperties properties;

    public ClaimDto unlock(ItemKind kind, Long itemId, Long claimId) {
        ClaimService<?, ?> service = claimServices.of(kind);
        ClaimDto claim = service.find(itemId, claimId)
                .orElseThrow(() -> new NotFoundException(kind.claimEntityName(), claimId));
        service.requireAuthorPhone(itemId);
        if (claim.paid() || claim.paymentVariantId() != null) {
            return claim;
        }
        service.reserveCheckout(claim.id());
        FourthwallProduct product;
        try {
            product = fourthwall.createDigitalProduct(
                    properties.productName().formatted(kind.label() + "-" + claim.id()),
                    properties.productDescription(),
                    properties.price());
        } catch (RuntimeException e) {
            service.releaseCheckout(claim.id());
            throw e;
        }
        ClaimDto attached = service.attachProduct(claim.id(), product);
        if (product.variantId().equals(attached.paymentVariantId())) {
            log.info("Checkout created for {} claim {}: Fourthwall product {}, variant {}",
                    kind.label(), claim.id(), product.productId(), product.variantId());
        } else {
            log.warn("Fourthwall product {} is orphaned: {} claim {} already had variant {}",
                    product.productId(), kind.label(), claim.id(), attached.paymentVariantId());
        }
        return attached;
    }
}
