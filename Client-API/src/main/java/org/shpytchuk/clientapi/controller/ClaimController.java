package org.shpytchuk.clientapi.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.config.FourthwallProperties;
import org.shpytchuk.clientapi.dto.ClaimDto;
import org.shpytchuk.clientapi.input.ContactInfoInput;
import org.shpytchuk.clientapi.service.ReturnService;
import org.shpytchuk.clientapi.service.found.FoundClaimService;
import org.shpytchuk.clientapi.service.lost.LostClaimService;
import org.shpytchuk.clientapi.service.payment.ClaimUnlockService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

@Controller
@Validated
@AllArgsConstructor
public class ClaimController {

    private final LostClaimService lostClaimService;
    private final FoundClaimService foundClaimService;
    private final ReturnService returnService;
    private final ClaimUnlockService unlockService;
    private final FourthwallProperties fourthwall;

    @QueryMapping
    public ClaimDto lostItemClaim(@Argument Long itemId, @Argument Long id) {
        return lostClaimService.find(itemId, id).orElse(null);
    }

    @QueryMapping
    public ClaimDto foundItemClaim(@Argument Long itemId, @Argument Long id) {
        return foundClaimService.find(itemId, id).orElse(null);
    }

    @SchemaMapping(typeName = "Claim")
    public String checkoutUrl(ClaimDto claim) {
        return claim.paymentVariantId() == null ? null : fourthwall.checkoutUrl(claim.paymentVariantId());
    }

    @MutationMapping
    public ClaimDto claimLostItem(@Argument Long id, @Argument @Valid ContactInfoInput contact) {
        return lostClaimService.claim(id, contact);
    }

    @MutationMapping
    public ClaimDto claimFoundItem(@Argument Long id, @Argument @Valid ContactInfoInput contact) {
        return foundClaimService.claim(id, contact);
    }

    @MutationMapping
    public boolean confirmReturn(@Argument String token) {
        return returnService.confirm(token);
    }

    @MutationMapping
    public ClaimDto unlockLostItemClaim(@Argument Long itemId, @Argument Long id) {
        return unlockService.unlockLost(itemId, id);
    }

    @MutationMapping
    public ClaimDto unlockFoundItemClaim(@Argument Long itemId, @Argument Long id) {
        return unlockService.unlockFound(itemId, id);
    }
}
