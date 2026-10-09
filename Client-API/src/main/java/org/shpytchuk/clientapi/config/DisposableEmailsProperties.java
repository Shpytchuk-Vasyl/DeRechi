package org.shpytchuk.clientapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "derechi.disposable-emails")
public record DisposableEmailsProperties(String url, Duration refreshInterval, Duration timeout) {

    public DisposableEmailsProperties {
        requirePositive(refreshInterval, "refresh-interval");
        requirePositive(timeout, "timeout");
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || !value.isPositive()) {
            throw new IllegalArgumentException("derechi.disposable-emails." + name + " must be positive: " + value);
        }
    }
}
