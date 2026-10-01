package org.shpytchuk.adminapi.view;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FormatsTest {

    private final Formats formats = new Formats();

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void formatsHryvniasForUkrainian() {
        LocaleContextHolder.setLocale(Locale.of("uk", "UA"));

        String money = formats.money(1500, "UAH");

        assertThat(digits(money)).isEqualTo("1500");
        assertThat(money).containsAnyOf("₴", "UAH");
    }

    @Test
    void formatsZlotyForPolish() {
        LocaleContextHolder.setLocale(Locale.of("pl", "PL"));

        String money = formats.money(1500, "PLN");

        assertThat(digits(money)).isEqualTo("1500");
        assertThat(money).containsAnyOf("zł", "PLN");
    }

    @Test
    void usesTheItemCurrencyNotTheLocaleOne() {
        LocaleContextHolder.setLocale(Locale.of("en", "GB"));

        String zloty = formats.money(1500, "PLN");
        String hryvnia = formats.money(1500, "uah");

        assertThat(digits(zloty)).isEqualTo("1500");
        assertThat(zloty).containsAnyOf("zł", "PLN").doesNotContain("£");
        assertThat(hryvnia).containsAnyOf("₴", "UAH").doesNotContain("£");
    }

    @Test
    void dropsFractionDigitsAndHandlesNull() {
        LocaleContextHolder.setLocale(Locale.of("de", "DE"));

        assertThat(digits(formats.money(1500, "EUR"))).isEqualTo("1500");
        assertThat(formats.money(null, "EUR")).isEmpty();
    }

    @Test
    void formatsAnInstantAsDateAndTimeInTheServerZone() {
        LocaleContextHolder.setLocale(Locale.of("uk", "UA"));
        Instant instant = Instant.parse("2026-03-15T09:05:00Z");
        ZonedDateTime local = instant.atZone(ZoneId.systemDefault());

        String text = formats.dateTime(instant);

        assertThat(text)
                .contains(String.valueOf(local.getYear()))
                .contains("%02d:%02d".formatted(local.getHour(), local.getMinute()))
                .doesNotContain("T", "Z", "2026-03-15");
        assertThat(formats.dateTime(null)).isEmpty();
    }

    private static String digits(String text) {
        return text.chars()
                .filter(Character::isDigit)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
