package org.shpytchuk.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "derechi.archive")
public record ArchiveProperties(String queue) {

    public ArchiveProperties {
        if (queue == null || queue.isBlank()) {
            throw new IllegalArgumentException("derechi.archive.queue must not be blank");
        }
    }
}
