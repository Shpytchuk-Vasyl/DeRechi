package org.shpytchuk.telegramingest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * @param fallbackModel sent in OpenRouter's {@code models} list after {@code model}; empty for none
 * @param dailyLimit    calls per UTC day, a safety net against a loop or a flood
 */
@ConfigurationProperties(prefix = "derechi.openrouter")
public record OpenRouterProperties(String url, String apiKey, String model, String fallbackModel,
                                   Duration timeout, int maxAttempts, Duration retryDelay, int dailyLimit) {

    public OpenRouterProperties {
        url = StringUtils.hasText(url) ? StringUtils.trimTrailingCharacter(url.trim(), '/') : "https://openrouter.ai/api/v1";
        apiKey = apiKey == null ? "" : apiKey.trim();
        model = StringUtils.hasText(model) ? model.trim() : "google/gemini-3.1-flash-lite";
        fallbackModel = fallbackModel == null ? "" : fallbackModel.trim();
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
        maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
        retryDelay = retryDelay == null ? Duration.ofSeconds(1) : retryDelay;
        dailyLimit = dailyLimit <= 0 ? 300 : dailyLimit;
    }

    public boolean configured() {
        return !apiKey.isEmpty();
    }
}
