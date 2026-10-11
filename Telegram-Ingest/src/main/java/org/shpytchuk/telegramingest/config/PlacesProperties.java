package org.shpytchuk.telegramingest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

@ConfigurationProperties(prefix = "derechi.places")
public record PlacesProperties(String url, String apiKey, Duration timeout, int maxAttempts, Duration retryDelay) {

    public PlacesProperties {
        url = StringUtils.hasText(url) ? StringUtils.trimTrailingCharacter(url.trim(), '/') : "https://places.googleapis.com/v1";
        apiKey = apiKey == null ? "" : apiKey.trim();
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
        retryDelay = retryDelay == null ? Duration.ofSeconds(1) : retryDelay;
    }

    public boolean configured() {
        return !apiKey.isEmpty();
    }
}
