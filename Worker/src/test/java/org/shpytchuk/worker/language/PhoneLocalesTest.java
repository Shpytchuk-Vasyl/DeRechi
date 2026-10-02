package org.shpytchuk.worker.language;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneLocalesTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "+380671234567, uk",
            "+48512345678, pl",
            "+4930123456, de",
            "+43664123456, de",
            "+41441234567, de",
            "+33123456789, fr",
            "+3221234567, fr",
            "+352621123456, fr",
            "+37793123456, fr",
            "+16502530000, en",
            "+447911123456, en",
            "+40721234567, en",
    })
    void picksTheLanguageByThePhoneCountry(String phone, String language) {
        assertThat(PhoneLocales.of(phone).getLanguage()).isEqualTo(language);
    }

    @Test
    void readsNumbersWrittenWithSpaces() {
        assertThat(PhoneLocales.of("+380 67 123 45 67").getLanguage()).isEqualTo("uk");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "not a phone", "0671234567"})
    void fallsBackToEnglishWhenThePhoneCannotBeRead(String phone) {
        assertThat(PhoneLocales.of(phone)).isEqualTo(Locale.ENGLISH);
    }
}
