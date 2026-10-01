package org.shpytchuk.adminapi.config.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "derechi.archive")
public record ArchiveProperties(String exchange) {

    public ArchiveProperties {
        if (exchange == null || exchange.isBlank()) {
            throw new IllegalArgumentException("derechi.archive.exchange must not be blank");
        }
    }
}
