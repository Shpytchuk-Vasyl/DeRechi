package org.shpytchuk.clientapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import java.time.Duration;

@ConfigurationProperties(prefix = "derechi.claims")
public record ClaimsProperties(String exchange, int unlockLimit, Duration unlockWindow) {

    public ClaimsProperties {
        Assert.hasText(exchange, "derechi.claims.exchange must be set");
        if (unlockLimit <= 0) {
            throw new IllegalArgumentException("derechi.claims.unlock-limit must be positive: " + unlockLimit);
        }
        if (unlockWindow == null || !unlockWindow.isPositive()) {
            throw new IllegalArgumentException("derechi.claims.unlock-window must be positive: " + unlockWindow);
        }
    }
}
