package org.shpytchuk.ingest.telegram;

import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.source.SourceSettings;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatSettingsParserTest {

    @Test
    void emptySpecHasNoOverrides() {
        assertThat(ChatSettingsParser.parse("", "UA", "uk")).isEmpty();
        assertThat(ChatSettingsParser.parse("   ", "UA", "uk")).isEmpty();
    }

    @Test
    void cityOnlyTakesTheDefaultRegionAndLocale() {
        Map<String, SourceSettings> chats = ChatSettingsParser.parse("zagubleno911=Львів", "UA", "uk");

        assertThat(chats).containsExactly(Map.entry("zagubleno911", new SourceSettings("UA", "Львів", "uk")));
    }

    @Test
    void readsRegionAndLocaleAndNormalizesTheKey() {
        Map<String, SourceSettings> chats = ChatSettingsParser.parse(" @Lost_Krakow = Kraków | pl | pl ", "UA", "uk");

        assertThat(chats).containsExactly(Map.entry("lost_krakow", new SourceSettings("PL", "Kraków", "pl")));
    }

    @Test
    void emptyCityMeansNoDefaultCity() {
        assertThat(ChatSettingsParser.parse("some_chat=|PL", "UA", "uk"))
                .containsExactly(Map.entry("some_chat", new SourceSettings("PL", null, "uk")));
    }

    @Test
    void numericChatIdIsAKeyToo() {
        assertThat(ChatSettingsParser.parse("-1001234567890=Kraków|PL|pl", "UA", "uk"))
                .containsKey("-1001234567890");
    }

    @Test
    void entriesAreSeparatedBySemicolonsOrNewLinesInOrder() {
        Map<String, SourceSettings> chats = ChatSettingsParser.parse("a=Львів;b=Київ\nc=Одеса;;\n", "UA", "uk");

        assertThat(chats.keySet()).containsExactly("a", "b", "c");
        assertThat(chats.get("b").defaultCity()).isEqualTo("Київ");
    }

    @Test
    void entryWithoutKeyIsRejected() {
        assertThatThrownBy(() -> ChatSettingsParser.parse("Львів", "UA", "uk"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key=city");
        assertThatThrownBy(() -> ChatSettingsParser.parse("=Львів", "UA", "uk"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void regionWithoutAKnownTimeZoneFailsTheStart() {
        assertThatThrownBy(() -> ChatSettingsParser.parse("chat=Kraków|POL", "UA", "uk"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No time zone known for region POL");
        assertThatThrownBy(() -> ChatSettingsParser.parse("chat=London|GB", "UA", "uk"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void timeZoneFollowsTheRegion() {
        assertThat(ChatSettingsParser.parse("lviv=Львів;krakow=Kraków|PL", "UA", "uk"))
                .extractingFromEntries(entry -> entry.getValue().zone().getId())
                .containsExactly("Europe/Kyiv", "Europe/Warsaw");
    }
}
