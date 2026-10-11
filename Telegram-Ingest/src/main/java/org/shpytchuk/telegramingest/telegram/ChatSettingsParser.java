package org.shpytchuk.telegramingest.telegram;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parses {@code derechi.telegram.chats}, which comes from one environment variable:
 *
 * <pre>
 * zagubleno911=Львів;kyiv_lost=Київ;-1001234567890=Kraków|PL|pl;some_chat=|PL
 * </pre>
 *
 * Each entry is {@code key=city[|region[|locale]]}; the key is a username (without {@code @}, any
 * case) or a numeric chat id; an empty city means no default city. Entries are separated by
 * {@code ;} or new lines.
 */
public final class ChatSettingsParser {

    private ChatSettingsParser() {
    }

    public static Map<String, ChatSettings> parse(String spec, String defaultRegion, String defaultLocale) {
        Map<String, ChatSettings> result = new LinkedHashMap<>();
        for (String entry : spec.split("[;\\n]")) {
            if (entry.isBlank()) {
                continue;
            }
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("derechi.telegram.chats: expected key=city, got '" + entry.trim() + "'");
            }
            String key = entry.substring(0, eq).trim().replaceFirst("^@", "").toLowerCase(Locale.ROOT);
            String[] parts = entry.substring(eq + 1).split("\\|", -1);
            String city = parts[0].trim();
            String region = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim().toUpperCase(Locale.ROOT) : defaultRegion;
            String locale = parts.length > 2 && !parts[2].isBlank() ? parts[2].trim() : defaultLocale;
            if (region.length() != 2) {
                throw new IllegalArgumentException("derechi.telegram.chats: region of '" + key + "' must be two letters");
            }
            result.put(key, new ChatSettings(region, city.isEmpty() ? null : city, locale));
        }
        return result;
    }
}
