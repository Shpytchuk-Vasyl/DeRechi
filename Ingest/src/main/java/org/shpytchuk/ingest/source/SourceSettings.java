package org.shpytchuk.ingest.source;

import org.jspecify.annotations.Nullable;

import java.time.ZoneId;
import java.util.Map;

public record SourceSettings(String region, @Nullable String defaultCity, String locale) {

    private static final Map<String, ZoneId> ZONES = Map.of(
            "UA", ZoneId.of("Europe/Kyiv"),
            "PL", ZoneId.of("Europe/Warsaw"),
            "DE", ZoneId.of("Europe/Berlin"),
            "FR", ZoneId.of("Europe/Paris"));

    public SourceSettings {
        requireKnownRegion(region);
    }

    public static void requireKnownRegion(String region) {
        if (!ZONES.containsKey(region)) {
            throw new IllegalArgumentException("No time zone known for region " + region + ", expected one of "
                    + ZONES.keySet());
        }
    }

    public ZoneId zone() {
        return ZONES.get(region);
    }
}
