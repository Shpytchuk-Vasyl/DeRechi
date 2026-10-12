package org.shpytchuk.ingest.pipeline;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.image.RedactionMode;
import org.shpytchuk.ingest.model.Outcome;
import org.shpytchuk.ingest.telegram.TelegramSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class IngestMetrics {

    private final MeterRegistry registry;
    private final Clock clock;
    private final AtomicLong lastReceived = new AtomicLong();

    public IngestMetrics(MeterRegistry registry, Clock clock, TelegramSource source) {
        this.registry = registry;
        this.clock = clock;
        received();
        Gauge.builder("derechi.ingest.telegram.authorized", source, s -> s.authorized() ? 1 : 0)
                .description("1 while the Telegram session is authorized")
                .register(registry);
        Gauge.builder("derechi.ingest.last.received", lastReceived, AtomicLong::get)
                .baseUnit("seconds")
                .description("Epoch second of the last post received from a source chat")
                .register(registry);
    }

    public void received() {
        lastReceived.set(clock.instant().getEpochSecond());
    }

    public void outcome(Outcome outcome) {
        registry.counter("derechi.ingest.posts", "outcome", outcome.tag()).increment();
    }

    public void published(String publisher, String outcome) {
        registry.counter("derechi.ingest.published", "publisher", publisher, "outcome", outcome).increment();
    }

    public void phoneSource(String source) {
        registry.counter("derechi.ingest.phone.source", "source", source).increment();
    }

    public void redacted(RedactionMode mode) {
        registry.counter("derechi.ingest.image.redacted", "mode", mode.name().toLowerCase()).increment();
    }

    public void llm(String model, Duration duration, @Nullable BigDecimal cost) {
        registry.timer("derechi.ingest.llm.duration", "model", model).record(duration);
        if (cost != null) {
            Counter.builder("derechi.ingest.llm.cost").baseUnit("usd").tag("model", model).register(registry)
                    .increment(cost.doubleValue());
        }
    }
}
