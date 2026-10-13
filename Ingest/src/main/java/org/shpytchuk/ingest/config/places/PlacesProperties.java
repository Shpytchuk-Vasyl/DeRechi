package org.shpytchuk.ingest.config.places;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

@ConfigurationProperties(prefix = "derechi.places")
public record PlacesProperties(Api api, String url, String apiKey, Duration timeout, int maxAttempts, Duration retryDelay) {

    public PlacesProperties {
        api = api == null ? Api.LEGACY : api;
        url = StringUtils.hasText(url) ? StringUtils.trimTrailingCharacter(url.trim(), '/') : api.defaultUrl;
        apiKey = apiKey == null ? "" : apiKey.trim();
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
        retryDelay = retryDelay == null ? Duration.ofSeconds(1) : retryDelay;
    }

    public boolean configured() {
        return !apiKey.isEmpty();
    }

    public enum Api {
        NEW("https://places.googleapis.com/v1"),
        LEGACY("https://maps.googleapis.com/maps/api/place");

        private final String defaultUrl;

        Api(String defaultUrl) {
            this.defaultUrl = defaultUrl;
        }
    }
}
