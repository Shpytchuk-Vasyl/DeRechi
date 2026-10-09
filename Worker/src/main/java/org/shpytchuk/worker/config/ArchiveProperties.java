package org.shpytchuk.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties(prefix = "derechi.archive")
public record ArchiveProperties(String queue) {

    public ArchiveProperties {
        Assert.hasText(queue, "derechi.archive.queue must not be blank");
    }
}
