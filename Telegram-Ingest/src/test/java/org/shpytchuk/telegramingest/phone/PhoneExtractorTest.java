package org.shpytchuk.telegramingest.phone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The table of spec 6.2: numbers written the way people write them in Ukrainian channels. */
class PhoneExtractorTest {

    private final PhoneExtractor extractor = new PhoneExtractor();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Загубив гаманець, тел. 067 123 45 67, дякую   | +380671234567",
            "Дзвоніть +38 (067) 123-45-67                  | +380671234567",
            "0671234567                                    | +380671234567",
            "номер 0 67 123 45 67                          | +380671234567",
            "Пишіть 380671234567                           | +380671234567",
            "067-123-45-67або вайбер                       | +380671234567",
            "Kontakt +48 512 345 678                       | +48512345678",
    })
    void findsTheNumber(String text, String e164) {
        List<FoundPhone> found = extractor.find(text.strip(), "UA");

        assertThat(found).extracting(FoundPhone::e164).containsExactly(e164);
    }

    @Test
    void positionsPointAtTheNumberInTheText() {
        String text = "Тел.: 067 123 45 67!";

        FoundPhone phone = extractor.find(text, "UA").getFirst();

        assertThat(text.substring(phone.start(), phone.end())).isEqualTo(phone.raw()).isEqualTo("067 123 45 67");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Картка для винагороди 4149 6293 1234 5678",
            "Загубив 12.10.2026 о 14:30 біля вокзалу",
            "Паспорт серія КВ 123456 на ім'я Петренко",
            "Kontakt 512 345 678",
    })
    void doesNotTakeCardsDatesDocumentsOrForeignNumbersWithoutCode(String text) {
        assertThat(extractor.find(text, "UA")).isEmpty();
    }

    @Test
    void numberWrittenTwiceIsFoundTwice() {
        List<FoundPhone> found = extractor.find("067 123 45 67 або +380671234567, чи 050 111 22 33", "UA");

        assertThat(found).extracting(FoundPhone::e164)
                .containsExactly("+380671234567", "+380671234567", "+380501112233");
    }

    @Test
    void emptyTextHasNoNumbers() {
        assertThat(extractor.find("", "UA")).isEmpty();
    }

    @Test
    void profilePhoneTelegramGivesWithoutPlusIsReadAsInternational() {
        assertThat(extractor.profilePhone("380671234567")).contains("+380671234567");
        assertThat(extractor.profilePhone("48512345678")).contains("+48512345678");
    }

    @Test
    void invalidProfilePhoneGivesNothing() {
        assertThat(extractor.profilePhone("12345")).isEmpty();
        assertThat(extractor.profilePhone("0671234567")).isEmpty();
        assertThat(extractor.profilePhone("")).isEmpty();
    }
}
