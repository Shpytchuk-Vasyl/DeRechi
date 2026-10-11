package org.shpytchuk.telegramingest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Period;

/**
 * The pipeline itself.
 *
 * @param hashKey           HMAC key of {@code telegram_post.phone_hash}
 * @param duplicateWindow   how long a contact phone marks later posts as duplicates
 * @param maxAge            notices older than this are skipped (Client-API accepts 30 days)
 * @param siteUrl           base of the notice links
 * @param siteLocale        locale segment of the notice links
 * @param minConfidence     extractions below it are skipped as {@code rejected}
 */
@ConfigurationProperties(prefix = "derechi.ingest")
public record IngestProperties(
        IngestMode mode,
        String hashKey,
        Duration duplicateWindow,
        int maxPhotos,
        Period maxAge,
        String siteUrl,
        String siteLocale,
        double minConfidence) {

    public IngestProperties {
        mode = mode == null ? IngestMode.DRY_RUN : mode;
        if (!StringUtils.hasText(hashKey)) {
            throw new IllegalArgumentException("derechi.ingest.hash-key must not be empty");
        }
        duplicateWindow = duplicateWindow == null ? Duration.ofDays(14) : duplicateWindow;
        maxPhotos = maxPhotos <= 0 ? 3 : maxPhotos;
        maxAge = maxAge == null ? Period.ofDays(30) : maxAge;
        siteUrl = StringUtils.hasText(siteUrl) ? StringUtils.trimTrailingCharacter(siteUrl.trim(), '/') : "http://localhost:3000";
        siteLocale = StringUtils.hasText(siteLocale) ? siteLocale.trim() : "uk";
        if (minConfidence < 0 || minConfidence > 1) {
            throw new IllegalArgumentException("derechi.ingest.min-confidence must be within 0..1");
        }
    }

    public enum IngestMode {
        DRY_RUN,
        AUTO
    }
}
