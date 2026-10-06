package org.shpytchuk.clientapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "derechi.events")
public record EventsProperties(int bufferCapacity, int batchSize, Duration flushInterval) {

    public EventsProperties {
        if (bufferCapacity <= 0) {
            throw new IllegalArgumentException("derechi.events.buffer-capacity must be positive: " + bufferCapacity);
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("derechi.events.batch-size must be positive: " + batchSize);
        }
        if (flushInterval == null || flushInterval.isNegative() || flushInterval.isZero()) {
            throw new IllegalArgumentException("derechi.events.flush-interval must be positive: " + flushInterval);
        }
    }
}
