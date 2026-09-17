package org.shpytchuk.adminapi.i18n;

import org.junit.jupiter.api.Test;
import org.shpytchuk.adminapi.config.LocaleConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Бандли розходяться тихо: ключ додають в одну мову, а решта показують його ім'я замість тексту.
 * Тому набір ключів і аргументи звіряються тут, а не покладаються на інспекцію IDE.
 */
class MessagesTest {

    private static final String BASE_BUNDLE = "messages.properties";

    /** {0}, {1,number} тощо — усе, що MessageFormat підставить з аргументів. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)[^}]*}");

    @Test
    void everySupportedLanguageHasItsOwnBundle() {
        for (Locale locale : LocaleConfig.SUPPORTED) {
            assertThat(load(bundleName(locale)))
                    .as("бандл для %s", locale.getLanguage())
                    .isNotEmpty();
        }
    }

    @Test
    void allBundlesDeclareTheSameKeys() {
        Set<String> expected = new TreeSet<>(load(BASE_BUNDLE).stringPropertyNames());

        for (Locale locale : LocaleConfig.SUPPORTED) {
            String bundle = bundleName(locale);
            Set<String> actual = new TreeSet<>(load(bundle).stringPropertyNames());

            assertThat(difference(expected, actual)).as("%s: бракує ключів", bundle).isEmpty();
            assertThat(difference(actual, expected)).as("%s: зайві ключі", bundle).isEmpty();
        }
    }

    /**
     * Загублений {0} у перекладі — не помилка компіляції, але користувач побачить
     * речення без номера речі. Набір аргументів має збігатися в усіх мовах.
     */
    @Test
    void translationsKeepTheSamePlaceholders() {
        Map<String, Set<String>> expected = placeholders(load(BASE_BUNDLE));

        for (Locale locale : LocaleConfig.SUPPORTED) {
            String bundle = bundleName(locale);
            Map<String, Set<String>> actual = placeholders(load(bundle));

            Map<String, Set<String>> mismatched = new LinkedHashMap<>();
            expected.forEach((key, arguments) -> {
                if (!arguments.equals(actual.get(key))) {
                    mismatched.put(key, actual.get(key));
                }
            });

            assertThat(mismatched).as("%s: аргументи розійшлися з базовим бандлом", bundle).isEmpty();
        }
    }

    /**
     * Англійська живе в базовому бандлі — він же фолбек для невідомих мов.
     * Окремий messages_en.properties був би її другою копією, яка з часом і розійдеться.
     */
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
            throw new IllegalStateException("Не вдалося прочитати " + bundle, unreadable);
        }
        return properties;
    }
}
