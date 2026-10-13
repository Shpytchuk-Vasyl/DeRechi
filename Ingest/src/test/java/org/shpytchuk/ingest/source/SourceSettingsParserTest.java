package org.shpytchuk.ingest.source;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceSettingsParserTest {

    @Test
    void emptySpecHasNoOverrides() {
        assertThat(SourceSettingsParser.parse("derechi.telegram.chats", "", "UA", "uk")).isEmpty();
        assertThat(SourceSettingsParser.parse("derechi.telegram.chats", "   ", "UA", "uk")).isEmpty();
    }

    @Test
    void cityOnlyTakesTheDefaultRegionAndLocale() {
        Map<String, SourceSettings> chats = SourceSettingsParser.parse("derechi.telegram.chats", "zagubleno911=Львів", "UA", "uk");

        assertThat(chats).containsExactly(Map.entry("zagubleno911", new SourceSettings("UA", "Львів", "uk")));
    }

    @Test
    void readsRegionAndLocaleAndNormalizesTheKey() {
        Map<String, SourceSettings> chats = SourceSettingsParser.parse("derechi.telegram.chats", " @Lost_Krakow = Kraków | pl | pl ", "UA", "uk");

        assertThat(chats).containsExactly(Map.entry("lost_krakow", new SourceSettings("PL", "Kraków", "pl")));
    }

    @Test
    void emptyCityMeansNoDefaultCity() {
        assertThat(SourceSettingsParser.parse("derechi.telegram.chats", "some_chat=|PL", "UA", "uk"))
                .containsExactly(Map.entry("some_chat", new SourceSettings("PL", null, "uk")));
    }

    @Test
    void numericChatIdIsAKeyToo() {
        assertThat(SourceSettingsParser.parse("derechi.telegram.chats", "-1001234567890=Kraków|PL|pl", "UA", "uk"))
                .containsKey("-1001234567890");
    }

    @Test
    void entriesAreSeparatedBySemicolonsOrNewLinesInOrder() {
        Map<String, SourceSettings> chats = SourceSettingsParser.parse("derechi.telegram.chats", "a=Львів;b=Київ\nc=Одеса;;\n", "UA", "uk");

        assertThat(chats.keySet()).containsExactly("a", "b", "c");
        assertThat(chats.get("b").defaultCity()).isEqualTo("Київ");
    }

    @Test
    void entryWithoutKeyIsRejected() {
        assertThatThrownBy(() -> SourceSettingsParser.parse("derechi.telegram.chats", "Львів", "UA", "uk"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key=city");
        assertThatThrownBy(() -> SourceSettingsParser.parse("derechi.telegram.chats", "=Львів", "UA", "uk"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void regionWithoutAKnownTimeZoneFailsTheStart() {
        assertThatThrownBy(() -> SourceSettingsParser.parse("derechi.telegram.chats", "chat=Kraków|POL", "UA", "uk"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No time zone known for region POL");
        assertThatThrownBy(() -> SourceSettingsParser.parse("derechi.telegram.chats", "chat=London|GB", "UA", "uk"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void timeZoneFollowsTheRegion() {
        assertThat(SourceSettingsParser.parse("derechi.telegram.chats", "lviv=Львів;krakow=Kraków|PL", "UA", "uk"))
                .extractingFromEntries(entry -> entry.getValue().zone().getId())
                .containsExactly("Europe/Kyiv", "Europe/Warsaw");
    }
}
