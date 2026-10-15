package org.shpytchuk.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Period;

@ConfigurationProperties(prefix = "derechi.archive")
public record ArchiveProperties(Period maxAge) {

    public ArchiveProperties {
        if (maxAge == null || maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException("derechi.archive.max-age must be a positive period");
        }
    }
}
