package org.shpytchuk.clientapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "derechi.claims")
public record ClaimsProperties(String exchange, int unlockLimit, Duration unlockWindow) {

    public ClaimsProperties {
        if (exchange == null || exchange.isBlank()) {
            throw new IllegalArgumentException("derechi.claims.exchange must be set");
        }
        if (unlockLimit <= 0) {
            throw new IllegalArgumentException("derechi.claims.unlock-limit must be positive: " + unlockLimit);
        }
        if (unlockWindow == null || unlockWindow.isNegative() || unlockWindow.isZero()) {
            throw new IllegalArgumentException("derechi.claims.unlock-window must be positive: " + unlockWindow);
        }
    }
}
