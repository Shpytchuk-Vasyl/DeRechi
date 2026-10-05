package org.shpytchuk.clientapi.service.payment;

import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.config.ClaimsProperties;
import org.shpytchuk.clientapi.entity.detail.ContactInfo;
import org.shpytchuk.clientapi.exeption.UnlockLimitException;
import org.shpytchuk.clientapi.repository.found.FoundItemClaimRepository;
import org.shpytchuk.clientapi.repository.lost.LostItemClaimRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

@Service
@AllArgsConstructor
public class ClaimUnlockLimiter {

    private final LostItemClaimRepository lostClaimRepository;
    private final FoundItemClaimRepository foundClaimRepository;
    private final ClaimsProperties properties;

    @Transactional(propagation = Propagation.MANDATORY)
    public void check(ContactInfo claimant) {
        Duration window = properties.unlockWindow();
        Instant since = Instant.now().minus(window);
        String phone = claimant.getPhone();
        String email = claimant.getEmail();

        List<Instant> requested = Stream.concat(
                        lostClaimRepository.findPaymentRequestedSince(phone, email, since).stream(),
                        foundClaimRepository.findPaymentRequestedSince(phone, email, since).stream())
                .sorted()
                .toList();
        if (requested.size() >= properties.unlockLimit()) {
            throw new UnlockLimitException(requested.get(requested.size() - properties.unlockLimit()).plus(window));
        }
    }
}
