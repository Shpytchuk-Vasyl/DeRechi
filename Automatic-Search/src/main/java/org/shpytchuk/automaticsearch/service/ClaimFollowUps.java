package org.shpytchuk.automaticsearch.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.automaticsearch.entity.Claim;
import org.shpytchuk.automaticsearch.repository.ClaimRepository;
import org.shpytchuk.automaticsearch.repository.ContactInfoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Service
@AllArgsConstructor
public class ClaimFollowUps {

    private final ClaimRepositories repositories;
    private final ClaimNotifier notifier;
    private final ContactInfoRepository contactInfoRepository;
    private final Clock clock;

    @Transactional
    public boolean remindAuthor(ItemKind kind, Long claimId) {
        return remind(repositories.of(kind), claimId,
                claim -> claim.getAuthorRemindedAt() == null,
                claim -> notifier.remindAuthor(kind, claim),
                Claim::setAuthorRemindedAt);
    }

    @Transactional
    public boolean remindClaimant(ItemKind kind, Long claimId) {
        return remind(repositories.of(kind), claimId,
                claim -> claim.getClaimantRemindedAt() == null,
                claim -> notifier.remindClaimant(kind, claim),
                Claim::setClaimantRemindedAt);
    }

    @Transactional
    public boolean purge(ItemKind kind, Long claimId) {
        ClaimRepository<? extends Claim> claims = repositories.of(kind);
        Optional<? extends Claim> found = claims.findById(claimId);
        if (found.isEmpty()) {
            return false;
        }
        Long contactInfoId = found.get().getContactInfo().getId();
        claims.deleteAllByIdInBatch(List.of(claimId));
        contactInfoRepository.deleteAllByIdInBatch(List.of(contactInfoId));
        return true;
    }

    private <C extends Claim> boolean remind(ClaimRepository<C> claims,
                                                 Long claimId,
                                                 Predicate<Claim> due,
                                                 Consumer<Claim> send,
                                                 BiConsumer<Claim, Instant> stamp) {
        Optional<C> found = claims.findWithDetailsById(claimId)
                .filter(claim -> claim.isLive() && claim.getConfirmedAt() == null && due.test(claim));
        if (found.isEmpty()) {
            return false;
        }

        C claim = found.get();
        send.accept(claim);
        stamp.accept(claim, clock.instant());
        claims.save(claim);
        return true;
    }
}
