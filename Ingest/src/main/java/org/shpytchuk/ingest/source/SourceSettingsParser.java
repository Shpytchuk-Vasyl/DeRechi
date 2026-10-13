package org.shpytchuk.ingest.source;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parses per-chat settings such as {@code derechi.telegram.chats}, which come from one environment variable:
 *
 * <pre>
 * zagubleno911=Львів;kyiv_lost=Київ;-1001234567890=Kraków|PL|pl;some_chat=|PL
 * </pre>
 *
 * Each entry is {@code key=city[|region[|locale]]}; the key is a username (without {@code @}, any
 * case), a Facebook group name or a numeric id; an empty city means no default city. Entries are separated by
 * {@code ;} or new lines.
 */
public final class SourceSettingsParser {

    private SourceSettingsParser() {
    }

    public static Map<String, SourceSettings> parse(String property, String spec, String defaultRegion,
                                                    String defaultLocale) {
        Map<String, SourceSettings> result = new LinkedHashMap<>();
        for (String entry : spec.split("[;\\n]")) {
            if (entry.isBlank()) {
                continue;
            }
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException(property + ": expected key=city, got '" + entry.trim() + "'");
            }
            String key = entry.substring(0, eq).trim().replaceFirst("^@", "").toLowerCase(Locale.ROOT);
            String[] parts = entry.substring(eq + 1).split("\\|", -1);
            String city = parts[0].trim();
            String region = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim().toUpperCase(Locale.ROOT) : defaultRegion;
            String locale = parts.length > 2 && !parts[2].isBlank() ? parts[2].trim() : defaultLocale;
            result.put(key, new SourceSettings(region, city.isEmpty() ? null : city, locale));
        }
        return result;
    }
}
