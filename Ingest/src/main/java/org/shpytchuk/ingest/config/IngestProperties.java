package org.shpytchuk.ingest.config;

import org.shpytchuk.ingest.model.ItemKind;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Period;
import java.util.List;
import java.util.Locale;

/**
 * The pipeline itself.
 *
 * @param hashKey           HMAC key of {@code ingest_post.phone_hash}
 * @param duplicateWindow   how long a contact phone marks later posts as duplicates
 * @param maxAge            notices older than this are skipped (Client-API accepts 30 days)
 * @param siteUrl           base of the notice links
 * @param siteLocale        locale segment of the notice links
 * @param minConfidence     extractions below it are skipped as {@code rejected}
 */
@ConfigurationProperties(prefix = "derechi.ingest")
public record IngestProperties(
        List<String> publishers,
        String hashKey,
        Duration duplicateWindow,
        int maxPhotos,
        Period maxAge,
        String siteUrl,
        String siteLocale,
        double minConfidence) {

    public IngestProperties {
        publishers = publishers == null ? List.of() : publishers.stream()
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .filter(name -> !name.isEmpty())
                .distinct()
                .toList();
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

    public String itemUrl(ItemKind kind, long itemId) {
        return "%s/%s/%s/%d".formatted(siteUrl, siteLocale, kind.path(), itemId);
    }
}
