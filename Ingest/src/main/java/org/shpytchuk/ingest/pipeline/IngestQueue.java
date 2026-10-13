package org.shpytchuk.ingest.pipeline;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.ingest.exception.BudgetExceededException;
import org.shpytchuk.ingest.model.Outcome;
import org.shpytchuk.ingest.source.IncomingPost;
import org.shpytchuk.ingest.source.PostKey;
import org.shpytchuk.ingest.source.PostSources;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@Slf4j
public class IngestQueue {

    private final BlockingQueue<IncomingPost> queue = new LinkedBlockingQueue<>();
    private final Set<PostKey> queued = ConcurrentHashMap.newKeySet();
    private final Set<PostKey> postponed = ConcurrentHashMap.newKeySet();
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
        } catch (BudgetExceededException e) {
            postpone(post, e);
        } catch (RuntimeException e) {
            log.error("Post {} crashed the pipeline", post.key(), e);
        } finally {
            queued.remove(post.key());
        }
    }

    private void postpone(IncomingPost post, BudgetExceededException e) {
        metrics.outcome(Outcome.BUDGET);
        if (postponed.add(post.key())) {
            scheduler.schedule(() -> {
                postponed.remove(post.key());
                offer(post);
            }, e.retryAt());
        }
        log.warn("Post {} postponed until {}: {}", post.key(), e.retryAt(), e.getMessage());
    }

    @PreDestroy
    public void stop() {
        Thread current = worker;
        if (current != null) {
            current.interrupt();
        }
    }
}
