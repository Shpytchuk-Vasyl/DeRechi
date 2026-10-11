package org.shpytchuk.telegramingest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

@ConfigurationProperties(prefix = "derechi.api")
public record ApiProperties(String uri, Duration timeout, int maxAttempts, Duration retryDelay) {

    public ApiProperties {
        uri = StringUtils.hasText(uri) ? StringUtils.trimTrailingCharacter(uri.trim(), '/') : "http://localhost:8080";
        timeout = timeout == null ? Duration.ofSeconds(15) : timeout;
        maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
        retryDelay = retryDelay == null ? Duration.ofSeconds(1) : retryDelay;
    }
}
