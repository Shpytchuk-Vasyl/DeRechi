package org.shpytchuk.worker.cron;

import lombok.AllArgsConstructor;
import org.shpytchuk.worker.config.ClaimsProperties;
import org.shpytchuk.worker.entity.claim.Claim;
import org.shpytchuk.worker.repository.ClaimRepository;
import org.shpytchuk.worker.service.ClaimRepositories;
import org.shpytchuk.worker.service.ItemArchiver;
import org.shpytchuk.worker.service.ItemKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

@Component
@AllArgsConstructor
public class ClaimFollowUpJob {

    private static final Logger log = LoggerFactory.getLogger(ClaimFollowUpJob.class);

    private final ClaimRepositories repositories;
    private final ClaimFollowUps followUps;
    private final ItemArchiver archiver;
    private final ClaimsProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${derechi.claims.check-every}")
    public void run() {
        Instant now = clock.instant();
        for (ItemKind kind : ItemKind.values()) {
            followUp(kind, now);
        }
    }

    void followUp(ItemKind kind, Instant now) {
        ClaimRepository<? extends Claim> claims = repositories.of(kind);

        int authorReminders = each(kind, "author reminder for claim",
                ids(claims.findByItemNotNullAndConfirmedAtNullAndAuthorRemindedAtNullAndCreatedAtLessThanEqualOrderByIdAsc(
                        now.minus(properties.authorReminderAfter()))),
                id -> followUps.remindAuthor(kind, id));

        int claimantReminders = each(kind, "claimant reminder for claim",
                ids(claims.findByItemNotNullAndConfirmedAtNullAndClaimantRemindedAtNullAndAuthorRemindedAtLessThanEqualOrderByIdAsc(
                        now.minus(properties.claimantReminderAfter()))),
                id -> followUps.remindClaimant(kind, id));

        int confirmed = each(kind, "archive of confirmed item",
                itemIds(claims.findByItemNotNullAndConfirmedAtNotNull()),
                id -> archiver.archive(kind, id));

        int quiet = each(kind, "archive of quiet item",
                quietItems(claims, now.minus(properties.archiveAfter())),
                id -> archiver.archive(kind, id));

        int purged = each(kind, "deletion of expired claim",
                ids(claims.findByCreatedAtLessThanEqualOrderByIdAsc(now.minus(properties.retention()))),
                id -> followUps.purge(kind, id));

        if (authorReminders + claimantReminders + confirmed + quiet + purged > 0) {
            log.info("{} claims: {} author reminders, {} claimant reminders, {} confirmed and {} quiet items "
                            + "archived, {} expired claims deleted",
                    kind, authorReminders, claimantReminders, confirmed, quiet, purged);
        } else {
            log.debug("{} claims: nothing to follow up", kind);
        }
    }

    private static List<Long> quietItems(ClaimRepository<? extends Claim> claims, Instant cutoff) {
        return itemIds(claims.findByItemNotNullAndCreatedAtLessThanEqual(cutoff)).stream()
                .filter(itemId -> !claims.existsByItemIdAndCreatedAtGreaterThan(itemId, cutoff))
                .toList();
    }

    private static List<Long> ids(List<? extends Claim> claims) {
        return claims.stream().map(Claim::getId).toList();
    }

    private static List<Long> itemIds(List<? extends Claim> claims) {
        return claims.stream().map(claim -> claim.getItem().getId()).distinct().toList();
    }

    private static int each(ItemKind kind, String step, List<Long> ids, Predicate<Long> action) {
        int done = 0;
        for (Long id : ids) {
            try {
                if (action.test(id)) {
                    done++;
                }
            } catch (AmqpException unavailable) {
                log.warn("{} claims: {} {} failed, the broker is unavailable; the rest waits for the next run: {}",
                        kind, step, id, unavailable.getMessage());
                break;
            } catch (RuntimeException failed) {
                log.warn("{} claims: {} {} failed, retrying on the next run", kind, step, id, failed);
            }
        }
        return done;
    }
}
