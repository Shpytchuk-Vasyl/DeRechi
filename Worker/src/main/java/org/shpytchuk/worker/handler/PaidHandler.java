package org.shpytchuk.worker.handler;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.worker.entity.matching.Claim;
import org.shpytchuk.worker.event.ClaimEvent;
import org.shpytchuk.worker.repository.ClaimRepository;
import org.shpytchuk.worker.service.ClaimNotifier;
import org.shpytchuk.worker.service.ClaimRepositories;
import org.shpytchuk.worker.service.ItemKind;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

@AllArgsConstructor
@Slf4j
public class PaidHandler implements ClaimHandler {

    private static final String VERB = "paid";

    private final ItemKind kind;
    private final ClaimRepositories repositories;
    private final ClaimNotifier notifier;
    private final Clock clock;

    @Override
    public ItemKind kind() {
        return kind;
    }

    @Override
    public String verb() {
        return VERB;
    }

    @Override
    @Transactional
    public void handle(ClaimEvent event) {
        send(repositories.of(kind), event.getId());
    }

    private <C extends Claim> void send(ClaimRepository<C> claims, Long claimId) {
        Optional<C> found = claims.findWithDetailsById(claimId).filter(claim -> claim.notice() != null);
        if (found.isEmpty()) {
            log.info("{} claim {} is gone, nobody to send the author's contacts to", kind, claimId);
            return;
        }

        C claim = found.get();
        if (claim.getContactsSentAt() != null) {
            log.info("The author's contacts for {} claim {} were already sent at {}", kind, claimId,
                    claim.getContactsSentAt());
            return;
        }
        if (claim.getPaidAt() == null) {
            log.warn("{} claim {} is not paid, the author's contacts stay hidden", kind, claimId);
            return;
        }

        notifier.sendAuthorContacts(kind, claim);
        claim.setContactsSentAt(clock.instant());
        claims.save(claim);
    }
}
