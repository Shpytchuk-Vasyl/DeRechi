package org.shpytchuk.telegramingest.pipeline;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.telegramingest.telegram.IncomingPost;
import org.shpytchuk.telegramingest.telegram.TelegramSource;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@Slf4j
public class IngestQueue {

    private final BlockingQueue<IncomingPost> queue = new LinkedBlockingQueue<>();
    private final Set<String> queued = ConcurrentHashMap.newKeySet();
    private final TelegramSource source;
    private final IngestPipeline pipeline;
    private final PostLedger ledger;
    private final IngestMetrics metrics;
    private volatile Thread worker;

    public IngestQueue(TelegramSource source, IngestPipeline pipeline, PostLedger ledger, IngestMetrics metrics,
                       MeterRegistry registry) {
        this.source = source;
        this.pipeline = pipeline;
        this.ledger = ledger;
        this.metrics = metrics;
        registry.gaugeCollectionSize("derechi.ingest.queue", Tags.empty(), queue);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        ledger.parkInterrupted();
        worker = Thread.ofVirtual().name("ingest-pipeline").start(this::drain);
        source.start(this::offer);
    }

    void offer(IncomingPost post) {
        metrics.received();
        if (queued.add(key(post))) {
            queue.add(post);
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
            try {
                pipeline.process(post);
            } catch (RuntimeException e) {
                log.error("Post {}/{} crashed the pipeline", post.chatId(), post.messageId(), e);
            } finally {
                queued.remove(key(post));
            }
        }
    }

    @PreDestroy
    public void stop() {
        Thread current = worker;
        if (current != null) {
            current.interrupt();
        }
    }

    private static String key(IncomingPost post) {
        return post.chatId() + ":" + post.messageId();
    }
}
