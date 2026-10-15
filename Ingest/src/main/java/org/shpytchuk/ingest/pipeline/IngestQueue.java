package org.shpytchuk.ingest.pipeline;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.ingest.exception.StageUnavailableException;
import org.shpytchuk.ingest.source.IncomingPost;
import org.shpytchuk.ingest.source.PostKey;
import org.shpytchuk.ingest.source.PostSources;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@Slf4j
public class IngestQueue {

    private static final int MAX_RETRIES = 3;
    private static final Duration RETRY_DELAY = Duration.ofMinutes(10);

    private final BlockingQueue<IncomingPost> queue = new LinkedBlockingQueue<>();
    private final Set<PostKey> queued = ConcurrentHashMap.newKeySet();
    private final Set<PostKey> postponed = ConcurrentHashMap.newKeySet();
    private final Map<PostKey, Integer> retries = new ConcurrentHashMap<>();
    private final PostSources sources;
    private final IngestPipeline pipeline;
    private final PostLedger ledger;
    private final IngestMetrics metrics;
    private final TaskScheduler scheduler;
    private volatile Thread worker;

    public IngestQueue(PostSources sources, IngestPipeline pipeline, PostLedger ledger, IngestMetrics metrics,
                       MeterRegistry registry, TaskScheduler scheduler) {
        this.sources = sources;
        this.pipeline = pipeline;
        this.ledger = ledger;
        this.metrics = metrics;
        this.scheduler = scheduler;
        registry.gaugeCollectionSize("derechi.ingest.queue", Tags.empty(), queue);
        registry.gaugeCollectionSize("derechi.ingest.postponed", Tags.empty(), postponed);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        ledger.parkInterrupted();
        worker = Thread.ofVirtual().name("ingest-pipeline").start(this::drain);
        sources.start(this::offer);
    }

    void offer(IncomingPost post) {
        metrics.received();
        enqueue(post);
    }

    private void enqueue(IncomingPost post) {
        if (postponed.contains(post.key())) {
            return;
        }
        if (queued.add(post.key())) {
            queue.add(post);
            log.debug("Post {} queued, {} waiting", post.key(), queue.size());
        }
    }

    private void drain() {
        while (!Thread.currentThread().isInterrupted()) {
            IncomingPost post;
            try {
                post = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            handle(post);
        }
    }

    void handle(IncomingPost post) {
        try {
            pipeline.process(post);
            retries.remove(post.key());
        } catch (StageUnavailableException e) {
            retry(post, e);
        } catch (RuntimeException e) {
            retries.remove(post.key());
            log.error("Post {} crashed the pipeline", post.key(), e);
        } finally {
            queued.remove(post.key());
        }
    }

    private void retry(IncomingPost post, StageUnavailableException e) {
        metrics.outcome(e.outcome());
        int retry = retries.merge(post.key(), 1, Integer::sum);
        if (retry > MAX_RETRIES) {
            retries.remove(post.key());
            log.warn("Post {} dropped after {} retries: {}", post.key(), MAX_RETRIES, e.getMessage());
            return;
        }
        Instant retryAt = e.retryAt().orElseGet(() -> scheduler.getClock().instant().plus(RETRY_DELAY));
        offerAt(post, retryAt);
        log.warn("Post {} retry {}/{} at {}: {}", post.key(), retry, MAX_RETRIES, retryAt, e.getMessage());
    }

    private void offerAt(IncomingPost post, Instant at) {
        if (postponed.add(post.key())) {
            scheduler.schedule(() -> {
                postponed.remove(post.key());
                enqueue(post);
            }, at);
        }
    }

    @PreDestroy
    public void stop() {
        Thread current = worker;
        if (current != null) {
            current.interrupt();
        }
    }
}
