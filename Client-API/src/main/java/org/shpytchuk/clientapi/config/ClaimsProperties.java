package org.shpytchuk.clientapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "derechi.claims")
public record ClaimsProperties(String exchange) {

    public ClaimsProperties {
        if (exchange == null || exchange.isBlank()) {
            throw new IllegalArgumentException("derechi.claims.exchange must be set");
        }
    }
}
