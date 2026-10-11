package org.shpytchuk.clientapi.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.exeption.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@AllArgsConstructor
public class ReturnService {

    private final ClaimServices claimServices;

    public boolean confirm(String token) {
        return claimServices.all().stream()
                .map(service -> service.confirm(token))
                .flatMap(Optional::stream)
                .findFirst()
                .map(claim -> true)
                .orElseThrow(() -> new NotFoundException("Claim", token));
    }
}
