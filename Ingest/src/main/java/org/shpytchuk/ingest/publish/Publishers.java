package org.shpytchuk.ingest.publish;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.config.IngestProperties;
import org.shpytchuk.ingest.entity.PostStatus;
import org.shpytchuk.ingest.api.ai.llm.ExtractionResult;
import org.shpytchuk.ingest.api.ai.model.Outcome;
import org.shpytchuk.ingest.pipeline.IngestMetrics;
import org.shpytchuk.ingest.pipeline.Notice;
import org.shpytchuk.ingest.pipeline.PostLedger;
import org.shpytchuk.ingest.source.IncomingPost;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

@Component
@Slf4j
public class Publishers {

    private final List<String> names;
    private final @Nullable SitePublisher site;
    private final List<Publisher> publishers;
    private final PostLedger ledger;
    private final IngestMetrics metrics;

    public Publishers(IngestProperties properties, SitePublisher site, List<Publisher> available, PostLedger ledger,
                      IngestMetrics metrics) {
        Map<String, Publisher> byName = available.stream()
                .collect(Collectors.toUnmodifiableMap(Publisher::name, Function.identity()));
        this.names = properties.publishers();
        this.site = names.contains(SitePublisher.NAME) ? site : null;
        this.publishers = names.stream()
                .filter(name -> !name.equals(SitePublisher.NAME))
                .map(name -> require(byName, name))
                .toList();
        this.ledger = ledger;
        this.metrics = metrics;
        log.info("Notices are published to {}", names);
    }

    public List<String> names() {
        return names;
    }

    public void publish(IncomingPost post, Notice notice, ExtractionResult result, List<byte[]> photos) {
        SiteItem siteItem = null;
        if (site != null) {
            Optional<SiteItem> created = site.publish(post, notice);
            if (created.isEmpty()) {
                return;
            }
            siteItem = created.get();
        } else {
            ledger.record(post, PostStatus.PREVIEWED, notice.phoneHash(), notice.kind());
            metrics.outcome(Outcome.PREVIEWED);
            log.info("Post {} previewed as a {} notice", post.key(), notice.kind().path());
        }
        Publication publication = new Publication(post, notice, result, photos, siteItem);
        publishers.forEach(publisher -> publish(publisher, publication));
    }

    public void skipped(IncomingPost post, Outcome outcome, ExtractionResult result, List<byte[]> photos) {
        SkippedPost skipped = new SkippedPost(post, outcome, result, photos);
        publishers.forEach(publisher -> reportSkip(publisher, skipped));
    }

    private void publish(Publisher publisher, Publication publication) {
        try {
            publisher.publish(publication);
            metrics.published(publisher.name(), "ok");
        } catch (RuntimeException e) {
            metrics.published(publisher.name(), "failed");
            log.warn("Post {} was not published to {}: {}", publication.post().key(), publisher.name(),
                    getMostSpecificCause(e).toString());
        }
    }

    private void reportSkip(Publisher publisher, SkippedPost skipped) {
        try {
            publisher.skipped(skipped);
        } catch (RuntimeException e) {
            log.warn("Skip of post {} was not reported to {}: {}", skipped.post().key(), publisher.name(),
                    getMostSpecificCause(e).toString());
        }
    }

    private Publisher require(Map<String, Publisher> byName, String name) {
        Publisher publisher = byName.get(name);
        if (publisher == null) {
            throw new IllegalArgumentException("derechi.ingest.publishers: unknown publisher " + name + ", expected "
                    + SitePublisher.NAME + " or one of " + byName.keySet());
        }
        if (publisher.needsSiteItem() && site == null) {
            throw new IllegalArgumentException("derechi.ingest.publishers: " + name
                    + " links to the notice on the site, add " + SitePublisher.NAME + " to the list");
        }
        return publisher;
    }
}
