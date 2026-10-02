package org.shpytchuk.clientapi.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.exeption.NotFoundException;
import org.shpytchuk.clientapi.service.found.FoundClaimService;
import org.shpytchuk.clientapi.service.lost.LostClaimService;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ReturnService {

    private final LostClaimService lostClaimService;
    private final FoundClaimService foundClaimService;

    public boolean confirm(String token) {
        return lostClaimService.confirm(token)
                .or(() -> foundClaimService.confirm(token))
                .map(claim -> true)
                .orElseThrow(() -> new NotFoundException("Claim", token));
    }
}
