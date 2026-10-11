package org.shpytchuk.telegramingest.config;

import org.shpytchuk.telegramingest.telegram.ChatSettings;
import org.shpytchuk.telegramingest.telegram.ChatSettingsParser;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 *
 * @param apiId           application id from my.telegram.org; 0 means "not configured"
 * @param phone           only needed for the one-off {@code --login}
 * @param dataDir         TDLib database and downloads, a persistent volume in Docker
 * @param folder          name of the chat folder whose channels and groups are read
 * @param chats           per-chat overrides, {@code username=city[|region[|locale]]} separated by {@code ;}
 *                        or new lines, see {@link ChatSettingsParser}
 * @param defaultRegion   region for chats without an override
 * @param defaultLocale   geocoding language for chats without an override
 */
@ConfigurationProperties(prefix = "derechi.telegram")
public record TelegramProperties(
        int apiId,
        String apiHash,
        String phone,
        String dataDir,
        String folder,
        String chats,
        String defaultRegion,
        String defaultLocale,
        Duration albumWait,
        Duration folderRefresh,
        Duration catchUpMaxAge,
        int catchUpMaxMessages) {

    public TelegramProperties {
        apiHash = apiHash == null ? "" : apiHash.trim();
        phone = phone == null ? "" : phone.trim();
        dataDir = StringUtils.hasText(dataDir) ? dataDir : "tdlib";
        folder = StringUtils.hasText(folder) ? folder.trim() : "DeRechi";
        chats = chats == null ? "" : chats;
        defaultRegion = StringUtils.hasText(defaultRegion) ? defaultRegion.trim().toUpperCase() : "UA";
        defaultLocale = StringUtils.hasText(defaultLocale) ? defaultLocale.trim() : "uk";
        albumWait = albumWait == null ? Duration.ofSeconds(3) : albumWait;
        folderRefresh = folderRefresh == null ? Duration.ofMinutes(10) : folderRefresh;
        catchUpMaxAge = catchUpMaxAge == null ? Duration.ofHours(48) : catchUpMaxAge;
        catchUpMaxMessages = catchUpMaxMessages <= 0 ? 200 : catchUpMaxMessages;
        ChatSettingsParser.parse(chats, defaultRegion, defaultLocale);
    }

    public boolean configured() {
        return apiId > 0 && !apiHash.isEmpty();
    }

    public Map<String, ChatSettings> chatOverrides() {
        return ChatSettingsParser.parse(chats, defaultRegion, defaultLocale);
    }

    public ChatSettings settingsFor(String username, long chatId) {
        Map<String, ChatSettings> overrides = chatOverrides();
        if (username != null && overrides.containsKey(username.toLowerCase(Locale.ROOT))) {
            return overrides.get(username.toLowerCase(Locale.ROOT));
        }
        return overrides.getOrDefault(Long.toString(chatId), new ChatSettings(defaultRegion, null, defaultLocale));
    }
}
