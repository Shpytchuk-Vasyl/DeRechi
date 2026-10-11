package org.shpytchuk.worker.cron;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.shpytchuk.worker.config.ClaimsProperties;
import org.shpytchuk.worker.entity.matching.Claim;
import org.shpytchuk.worker.repository.ClaimRepository;
import org.shpytchuk.worker.service.ItemArchiver;
import org.shpytchuk.worker.service.ItemKind;
import org.shpytchuk.worker.service.ItemKinds;
import org.springframework.amqp.AmqpException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

@Component
@AllArgsConstructor
@Slf4j
public class ClaimFollowUpJob {

    private final ItemKinds kinds;
    private final ClaimFollowUps followUps;
    private final ItemArchiver archiver;
    private final ClaimsProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${derechi.claims.check-every}")
    @SchedulerLock(name = "claim-follow-up")
    public void run() {
        Instant now = clock.instant();
        for (ItemKind kind : ItemKind.values()) {
            followUp(kind, now);
        }
    }

    void followUp(ItemKind kind, Instant now) {
        ClaimRepository<? extends Claim> claims = kinds.claims(kind);

        List<Step> steps = List.of(
                new Step("author reminder for claim", "author reminders",
                        () -> ids(claims.findByItemNotNullAndConfirmedAtNullAndAuthorRemindedAtNullAndCreatedAtLessThanEqualOrderByIdAsc(
                                now.minus(properties.authorReminderAfter()))),
                        id -> followUps.remindAuthor(kind, id)),
                new Step("claimant reminder for claim", "claimant reminders",
                        () -> ids(claims.findByItemNotNullAndConfirmedAtNullAndClaimantRemindedAtNullAndAuthorRemindedAtLessThanEqualOrderByIdAsc(
                                now.minus(properties.claimantReminderAfter()))),
                        id -> followUps.remindClaimant(kind, id)),
                new Step("archive of confirmed item", "confirmed items archived",
                        () -> itemIds(claims.findByItemNotNullAndConfirmedAtNotNull()),
                        id -> archiver.archive(kind, id)),
                new Step("archive of quiet item", "quiet items archived",
                        () -> quietItems(claims, now.minus(properties.archiveAfter())),
                        id -> archiver.archive(kind, id)),
                new Step("deletion of expired claim", "expired claims deleted",
                        () -> ids(claims.findByCreatedAtLessThanEqualOrderByIdAsc(now.minus(properties.retention()))),
                        id -> followUps.purge(kind, id)));

        int total = 0;
        StringJoiner summary = new StringJoiner(", ");
        for (Step step : steps) {
            int done = each(kind, step);
            total += done;
            summary.add(done + " " + step.summary());
        }

        if (total > 0) {
            log.info("{} claims: {}", kind, summary);
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

    private static int each(ItemKind kind, Step step) {
        int done = 0;
        for (Long id : step.find().get()) {
            try {
                if (step.act().test(id)) {
                    done++;
                }
            } catch (AmqpException unavailable) {
                log.warn("{} claims: {} {} failed, the broker is unavailable; the rest waits for the next run: {}",
                        kind, step.name(), id, getMostSpecificCause(unavailable).toString());
                break;
            } catch (RuntimeException failed) {
                log.warn("{} claims: {} {} failed, retrying on the next run: {}",
                        kind, step.name(), id, getMostSpecificCause(failed).toString());
            }
        }
        return done;
    }

    private record Step(String name, String summary, Supplier<List<Long>> find, Predicate<Long> act) {
    }
}
