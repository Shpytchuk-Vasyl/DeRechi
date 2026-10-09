package org.shpytchuk.adminapi.config.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties(prefix = "derechi.archive")
public record ArchiveProperties(String exchange) {

    public ArchiveProperties {
        Assert.hasText(exchange, "derechi.archive.exchange must not be blank");
    }
}
