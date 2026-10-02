package org.shpytchuk.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "derechi.claims")
public record ClaimsProperties(
        String queue,
        String exchange,
        String siteUrl,
        Duration checkEvery,
        Duration authorReminderAfter,
        Duration claimantReminderAfter,
        Duration archiveAfter,
        Duration retention) {

    public ClaimsProperties {
        requireText(queue, "queue");
        requireText(exchange, "exchange");
        requireText(siteUrl, "site-url");
        requirePositive(checkEvery, "check-every");
        requirePositive(authorReminderAfter, "author-reminder-after");
        requirePositive(claimantReminderAfter, "claimant-reminder-after");
        requirePositive(archiveAfter, "archive-after");
        requirePositive(retention, "retention");
        siteUrl = siteUrl.strip().replaceAll("/+$", "");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("derechi.claims." + name + " must not be blank");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException("derechi.claims." + name + " must be a positive duration");
        }
    }
}
