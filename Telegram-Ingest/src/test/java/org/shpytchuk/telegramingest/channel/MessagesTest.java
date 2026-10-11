package org.shpytchuk.telegramingest.channel;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MessagesTest {

    private static final List<String> TRANSLATIONS = List.of("messages_uk.properties");
    private static final List<String> ALL = Stream.concat(Stream.of("messages.properties"), TRANSLATIONS.stream()).toList();

    @Test
    void everyBundleHasTheSameKeysAsTheFallback() throws IOException {
        Properties fallback = load("messages.properties");
        assertThat(fallback).isNotEmpty();

        for (String file : TRANSLATIONS) {
            assertThat(load(file).stringPropertyNames())
                    .as(file)
                    .containsExactlyInAnyOrderElementsOf(fallback.stringPropertyNames());
        }
    }

    @Test
    void textsHaveNoLongDashesAndNoBlankValues() throws IOException {
        for (String file : ALL) {
            Properties bundle = load(file);
            for (String key : bundle.stringPropertyNames()) {
                assertThat(bundle.getProperty(key)).as(file + " " + key)
                        .isNotBlank()
                        .doesNotContain("—")
                        .doesNotContain("–");
            }
        }
    }

    private static Properties load(String file) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = MessagesTest.class.getClassLoader().getResourceAsStream(file)) {
            assertThat(in).as(file).isNotNull();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return properties;
    }
}
