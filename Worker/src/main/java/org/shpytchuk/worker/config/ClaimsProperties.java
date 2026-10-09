package org.shpytchuk.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

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
        siteUrl = StringUtils.trimTrailingCharacter(siteUrl.strip(), '/');
    }

    private static void requireText(String value, String name) {
        Assert.hasText(value, "derechi.claims." + name + " must not be blank");
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || !value.isPositive()) {
            throw new IllegalArgumentException("derechi.claims." + name + " must be a positive duration");
        }
    }
}
