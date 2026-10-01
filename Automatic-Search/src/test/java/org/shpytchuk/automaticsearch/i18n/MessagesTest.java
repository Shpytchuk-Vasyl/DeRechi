package org.shpytchuk.automaticsearch.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MessagesTest {

    private static final String BASE_BUNDLE = "messages.properties";

    private static final List<Locale> SUPPORTED = List.of(
            Locale.ENGLISH, Locale.of("uk"), Locale.of("pl"), Locale.GERMAN, Locale.FRENCH);

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)[^}]*}");

    @Test
    void everySupportedLanguageHasItsOwnBundle() {
        for (Locale locale : SUPPORTED) {
            assertThat(load(bundleName(locale)))
                    .as("bundle for %s", locale.getLanguage())
                    .isNotEmpty();
        }
    }

    @Test
    void allBundlesDeclareTheSameKeys() {
        Set<String> expected = new TreeSet<>(load(BASE_BUNDLE).stringPropertyNames());

        for (Locale locale : SUPPORTED) {
            String bundle = bundleName(locale);
            Set<String> actual = new TreeSet<>(load(bundle).stringPropertyNames());

            assertThat(difference(expected, actual)).as("%s: missing keys", bundle).isEmpty();
            assertThat(difference(actual, expected)).as("%s: extra keys", bundle).isEmpty();
        }
    }

    @Test
    void translationsKeepTheSamePlaceholders() {
        Map<String, Set<String>> expected = placeholders(load(BASE_BUNDLE));

        for (Locale locale : SUPPORTED) {
            String bundle = bundleName(locale);
            Map<String, Set<String>> actual = placeholders(load(bundle));

            Map<String, Set<String>> mismatched = new LinkedHashMap<>();
            expected.forEach((key, arguments) -> {
                if (!arguments.equals(actual.get(key))) {
                    mismatched.put(key, actual.get(key));
                }
            });

            assertThat(mismatched).as("%s: placeholders differ from the base bundle", bundle).isEmpty();
        }
    }

    private static String bundleName(Locale locale) {
        return Locale.ENGLISH.getLanguage().equals(locale.getLanguage())
                ? BASE_BUNDLE
                : "messages_%s.properties".formatted(locale.getLanguage());
    }

    private static Set<String> difference(Set<String> from, Set<String> without) {
        return from.stream().filter(key -> !without.contains(key))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Map<String, Set<String>> placeholders(Properties bundle) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String key : bundle.stringPropertyNames()) {
            Set<String> arguments = new TreeSet<>();
            Matcher matcher = PLACEHOLDER.matcher(bundle.getProperty(key));
            while (matcher.find()) {
                arguments.add(matcher.group(1));
            }
            result.put(key, arguments);
        }
        return result;
    }

    private static Properties load(String bundle) {
        Properties properties = new Properties();
        try (InputStream stream = MessagesTest.class.getClassLoader().getResourceAsStream(bundle)) {
            if (stream == null) {
                throw new IllegalStateException(bundle + " not found on the classpath");
            }
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException unreadable) {
            throw new IllegalStateException("Cannot read " + bundle, unreadable);
        }
        return properties;
    }
}
