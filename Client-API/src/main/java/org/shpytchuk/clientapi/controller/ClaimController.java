package org.shpytchuk.clientapi.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.dto.ClaimDto;
import org.shpytchuk.clientapi.input.ContactInfoInput;
import org.shpytchuk.clientapi.service.FoundClaimService;
import org.shpytchuk.clientapi.service.LostClaimService;
import org.shpytchuk.clientapi.service.ReturnService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

@Controller
@Validated
@AllArgsConstructor
public class ClaimController {

    private final LostClaimService lostClaimService;
    private final FoundClaimService foundClaimService;
    private final ReturnService returnService;

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
}
