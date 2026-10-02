package org.shpytchuk.clientapi.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.exeption.NotFoundException;
import org.shpytchuk.clientapi.service.found.FoundClaimService;
import org.shpytchuk.clientapi.service.lost.LostClaimService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class ReturnService {

    private final LostClaimService lostClaimService;
    private final FoundClaimService foundClaimService;

    @Transactional
    public boolean confirm(String token) {
        if (lostClaimService.confirm(token) || foundClaimService.confirm(token)) {
            return true;
        }
        throw new NotFoundException("Claim", token);
    }
}
