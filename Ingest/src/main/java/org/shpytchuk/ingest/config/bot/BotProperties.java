package org.shpytchuk.ingest.config.bot;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

@ConfigurationProperties(prefix = "derechi.bot")
public record BotProperties(String apiUrl, String token, String publishChat, String reviewChat,
                            String locale, Duration timeout, int maxAttempts, Duration retryDelay) {

    public BotProperties {
        apiUrl = StringUtils.hasText(apiUrl) ? StringUtils.trimTrailingCharacter(apiUrl.trim(), '/') : "https://api.telegram.org";
        token = token == null ? "" : token.trim();
        publishChat = publishChat == null ? "" : publishChat.trim();
        reviewChat = reviewChat == null ? "" : reviewChat.trim();
        locale = StringUtils.hasText(locale) ? locale.trim() : "uk";
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
        retryDelay = retryDelay == null ? Duration.ofSeconds(3) : retryDelay;
    }

    public boolean canPublish() {
        return !token.isEmpty() && !publishChat.isEmpty();
    }

    public boolean canPreview() {
        return !token.isEmpty() && !reviewChat.isEmpty();
    }
}
