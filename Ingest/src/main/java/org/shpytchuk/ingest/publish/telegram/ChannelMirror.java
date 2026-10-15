package org.shpytchuk.ingest.publish.telegram;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.ingest.config.IngestProperties;
import org.shpytchuk.ingest.config.bot.BotProperties;
import org.shpytchuk.ingest.config.events.EventsProperties;
import org.shpytchuk.ingest.event.ItemCreatedEvent;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.pipeline.IngestMetrics;
import org.shpytchuk.ingest.pipeline.PostLedger;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
@AllArgsConstructor
@Slf4j
public class ChannelMirror {

    private static final String POSTED = "posted";
    private static final String DELETED = "deleted";
    private static final String SITE = "site";
    private static final String ADMIN = "admin";

    private final ChannelPoster poster;
    private final PostLedger ledger;
    private final StoredPhotos photos;
    private final IngestProperties ingest;
    private final BotProperties bot;
    private final EventsProperties events;
    private final IngestMetrics metrics;
    private final Clock clock;

    public void created(ItemKind kind, ItemCreatedEvent event) {
        if (!enabled()) {
            log.debug("The channel is off, {} item {} is not posted", kind.path(), event.id());
            return;
        }
        awaitPipelinePublish();
        if (ledger.owns(kind, event.id())) {
            log.debug("{} item {} is already in the ledger, nothing to post", kind.path(), event.id());
            return;
        }
        try {
            poster.postFromSite(origin(event), kind, event.id(),
                    ChannelPost.of(kind, event, photos.load(event.image()), ingest.itemUrl(kind, event.id())));
            metrics.channel(POSTED, "ok");
        } catch (RuntimeException e) {
            metrics.channel(POSTED, "failed");
            throw e;
        }
    }

    public void removed(ItemKind kind, long itemId, String reason) {
        if (!enabled()) {
            log.debug("The channel is off, {} {} item {} is not looked up", reason, kind.path(), itemId);
            return;
        }
        try {
            if (poster.delete(kind, itemId)) {
                metrics.channel(DELETED, "ok");
                log.info("{} item {} is {}, its channel message is deleted", kind.path(), itemId, reason);
            } else {
                log.debug("{} item {} is {} and has no channel message", kind.path(), itemId, reason);
            }
        } catch (IngestException e) {
            metrics.channel(DELETED, "failed");
            log.warn("{} item {} is {}, but its channel message stays: {}", kind.path(), itemId, reason,
                    e.getMessage());
        }
    }

    private static String origin(ItemCreatedEvent event) {
        return ADMIN.equals(event.origin()) ? ADMIN : SITE;
    }

    private boolean enabled() {
        return ingest.publishers().contains(ChannelPoster.NAME) && bot.canPublish();
    }

    private void awaitPipelinePublish() {
        Instant deadline = clock.instant().plus(events.inFlightWait());
        while (ledger.publishing() && clock.instant().isBefore(deadline)) {
            try {
                Thread.sleep(events.inFlightPoll());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while the pipeline was publishing", e);
            }
        }
    }
}
