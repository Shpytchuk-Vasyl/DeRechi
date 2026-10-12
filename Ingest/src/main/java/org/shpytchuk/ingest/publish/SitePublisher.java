package org.shpytchuk.ingest.publish;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.api.derechi.DeRechiApi;
import org.shpytchuk.ingest.config.IngestProperties;
import org.shpytchuk.ingest.entity.PostStatus;
import org.shpytchuk.ingest.exception.RejectedItemException;
import org.shpytchuk.ingest.api.ai.model.Outcome;
import org.shpytchuk.ingest.pipeline.IngestMetrics;
import org.shpytchuk.ingest.pipeline.Notice;
import org.shpytchuk.ingest.pipeline.PostLedger;
import org.shpytchuk.ingest.source.IncomingPost;
import org.shpytchuk.ingest.api.storage.ImageUploader;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

@Component
@AllArgsConstructor
@Slf4j
public class SitePublisher {

    public static final String NAME = "site";

    private final IngestProperties properties;
    private final ImageUploader uploader;
    private final DeRechiApi api;
    private final PostLedger ledger;
    private final IngestMetrics metrics;

    public Optional<SiteItem> publish(IncomingPost post, Notice notice) {
        String imageKey = upload(post, notice);
        long rowId = ledger.record(post, PostStatus.PUBLISHING, notice.phoneHash(), notice.kind());
        try {

            long itemId = api.create(notice.newItem(imageKey));
            ledger.published(rowId, itemId);
            metrics.outcome(Outcome.PUBLISHED);
            log.info("Post {} published as {} item {}", post.key(), notice.kind().path(), itemId);
            return Optional.of(new SiteItem(notice.kind(), itemId, itemUrl(notice, itemId)));

        } catch (RejectedItemException e) {
            ledger.move(rowId, PostStatus.FAILED);
            log.warn("Post {} rejected by Client-API: {}", post.key(), e.getMessage());
        } catch (RuntimeException e) {
            ledger.move(rowId, PostStatus.NEEDS_CHECK);
            log.error("Post {} may or may not be on the site (NEEDS_CHECK): {}", post.key(),
                    getMostSpecificCause(e).toString());
        }
        metrics.outcome(Outcome.FAILED);
        return Optional.empty();
    }

    private @Nullable String upload(IncomingPost post, Notice notice) {
        byte[] photo = notice.photo();
        if (photo == null) {
            return null;
        }
        long started = System.nanoTime();
        String imageKey = uploader.upload(photo);
        log.debug("Post {} photo uploaded as {} in {} ms", post.key(), imageKey,
                (System.nanoTime() - started) / 1_000_000);
        return imageKey;
    }

    private String itemUrl(Notice notice, long itemId) {
        return "%s/%s/%s/%d".formatted(properties.siteUrl(), properties.siteLocale(), notice.kind().path(), itemId);
    }
}
