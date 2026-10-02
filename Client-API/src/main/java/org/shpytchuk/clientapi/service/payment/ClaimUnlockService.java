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

import java.util.Optional;

@Service
@AllArgsConstructor
public class ClaimUnlockService {

    private static final Logger log = LoggerFactory.getLogger(ClaimUnlockService.class);

    private final LostClaimService lostClaimService;
    private final FoundClaimService foundClaimService;
    private final FourthwallClient fourthwall;
    private final FourthwallProperties properties;

    public Optional<ClaimDto> status(String token) {
        return lostClaimService.findByToken(token).or(() -> foundClaimService.findByToken(token));
    }

    public ClaimDto unlock(String token) {
        return unlock(lostClaimService, token, "lost")
                .or(() -> unlock(foundClaimService, token, "found"))
                .orElseThrow(() -> new NotFoundException("Claim", token));
    }

    private Optional<ClaimDto> unlock(ClaimService<?, ?> service, String token, String kind) {
        return service.findByToken(token).map(claim -> {
            if (claim.paid() || claim.paymentVariantId() != null) {
                return claim;
            }
            FourthwallProduct product = fourthwall.createDigitalProduct(
                    properties.productName().formatted(kind + "-" + claim.id()),
                    properties.productDescription(),
                    properties.price());
            ClaimDto attached = service.attachProduct(claim.id(), product);
            if (!product.variantId().equals(attached.paymentVariantId())) {
                log.warn("Fourthwall product {} is orphaned: {} claim {} already had variant {}",
                        product.productId(), kind, claim.id(), attached.paymentVariantId());
            }
            return attached;
        });
    }
}
