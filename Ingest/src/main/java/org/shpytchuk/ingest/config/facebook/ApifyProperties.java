package org.shpytchuk.ingest.config.facebook;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;

@ConfigurationProperties(prefix = "derechi.apify")
public record ApifyProperties(
        String apiUrl,
        String token,
        String facebookActor,
        int resultsLimit,
        BigDecimal maxChargeUsd,
        Duration timeout) {

    private static final String DEFAULT_API_URL = "https://api.apify.com/v2";
    private static final String DEFAULT_FACEBOOK_ACTOR = "apify~facebook-groups-scraper";
    private static final int DEFAULT_RESULTS_LIMIT = 10;
    private static final BigDecimal DEFAULT_MAX_CHARGE_USD = new BigDecimal("0.05");
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(4);
    private static final Duration MAX_SYNC_RUN = Duration.ofMinutes(5);

    public ApifyProperties {
        apiUrl = StringUtils.hasText(apiUrl) ? StringUtils.trimTrailingCharacter(apiUrl.trim(), '/') : DEFAULT_API_URL;
        token = token == null ? "" : token.trim();
        facebookActor = StringUtils.hasText(facebookActor) ? facebookActor.trim() : DEFAULT_FACEBOOK_ACTOR;
        resultsLimit = resultsLimit <= 0 ? DEFAULT_RESULTS_LIMIT : resultsLimit;
        maxChargeUsd = maxChargeUsd == null || maxChargeUsd.signum() <= 0 ? DEFAULT_MAX_CHARGE_USD : maxChargeUsd;
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        if (timeout.compareTo(MAX_SYNC_RUN) > 0) {
            throw new IllegalArgumentException("derechi.apify.timeout must be at most " + MAX_SYNC_RUN
                    + ", Apify cuts a synchronous run there");
        }
    }

    public boolean configured() {
        return !token.isEmpty();
    }
}
