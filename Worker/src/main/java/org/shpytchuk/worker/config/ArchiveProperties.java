package org.shpytchuk.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import java.time.Period;

@ConfigurationProperties(prefix = "derechi.archive")
public record ArchiveProperties(String queue, Period maxAge) {

    public ArchiveProperties {
        Assert.hasText(queue, "derechi.archive.queue must not be blank");
        if (maxAge == null || maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException("derechi.archive.max-age must be a positive period");
        }
    }
}
