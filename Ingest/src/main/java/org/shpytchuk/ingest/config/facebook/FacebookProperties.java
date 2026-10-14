package org.shpytchuk.ingest.config.facebook;

import org.shpytchuk.ingest.source.SourceSettings;
import org.shpytchuk.ingest.source.SourceSettingsParser;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties(prefix = "derechi.facebook")
public record FacebookProperties(
        String cookies,
        String groups,
        String userAgent,
        Duration pollEvery,
        Duration pollJitter,
        Duration requestPause,
        int maxPages,
        Duration catchUpMaxAge,
        Duration timeout,
        String activeHours) {

    private static final String GROUPS_PROPERTY = "derechi.facebook.groups";
    private static final String ACTIVE_HOURS_PROPERTY = "derechi.facebook.active-hours";
    private static final String DEFAULT_REGION = "UA";
    private static final String DEFAULT_LOCALE = "uk";
    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36";

    public FacebookProperties {
        cookies = cookies == null ? "" : cookies.trim();
        groups = groups == null ? "" : groups;
        userAgent = StringUtils.hasText(userAgent) ? userAgent.trim() : DEFAULT_USER_AGENT;
        pollEvery = pollEvery == null ? Duration.ofHours(1) : pollEvery;
        pollJitter = pollJitter == null ? Duration.ofMinutes(20) : pollJitter;
        requestPause = requestPause == null ? Duration.ofSeconds(10) : requestPause;
        maxPages = maxPages <= 0 ? 10 : maxPages;
        catchUpMaxAge = catchUpMaxAge == null ? Duration.ofHours(48) : catchUpMaxAge;
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        activeHours = activeHours == null ? "" : activeHours.trim();
        SourceSettingsParser.parse(GROUPS_PROPERTY, groups, DEFAULT_REGION, DEFAULT_LOCALE);
        ActiveHours.parse(ACTIVE_HOURS_PROPERTY, activeHours);
    }

    public boolean configured() {
        return !cookies.isEmpty() && !groupSettings().isEmpty();
    }

    public ActiveHours hours() {
        return ActiveHours.parse(ACTIVE_HOURS_PROPERTY, activeHours);
    }

    public Map<String, SourceSettings> groupSettings() {
        return SourceSettingsParser.parse(GROUPS_PROPERTY, groups, DEFAULT_REGION, DEFAULT_LOCALE);
    }
}
