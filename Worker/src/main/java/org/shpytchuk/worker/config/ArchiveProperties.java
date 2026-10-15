package org.shpytchuk.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import java.time.Period;

@ConfigurationProperties(prefix = "derechi.archive")
public record ArchiveProperties(Period maxAge, String exchange) {

    public ArchiveProperties {
        if (maxAge == null || maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException("derechi.archive.max-age must be a positive period");
        }
        Assert.hasText(exchange, "derechi.archive.exchange must not be blank");
    }
}
