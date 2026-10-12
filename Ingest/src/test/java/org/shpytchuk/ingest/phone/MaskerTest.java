package org.shpytchuk.ingest.phone;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MaskerTest {

    private final PhoneExtractor extractor = new PhoneExtractor();
    private final Masker masker = new Masker();

    @Test
    void theSameNumberWrittenTwiceGetsOneReference() {
        String text = "Дзвоніть 067 123 45 67 або пишіть у вайбер +380671234567";

        MaskedText masked = mask(text);

        assertThat(masked.text()).isEqualTo("Дзвоніть [PHONE_1] або пишіть у вайбер [PHONE_1]");
        assertThat(masked.phones()).containsExactly(Map.entry("PHONE_1", "+380671234567"));
    }

    @Test
    void differentNumbersAreNumberedInOrderOfAppearance() {
        String text = "Олена 050 111 22 33, Петро 067 123 45 67, знову Олена 0501112233";

        MaskedText masked = mask(text);

        assertThat(masked.text()).isEqualTo("Олена [PHONE_1], Петро [PHONE_2], знову Олена [PHONE_1]");
        assertThat(masked.phones()).containsOnly(
                Map.entry("PHONE_1", "+380501112233"),
                Map.entry("PHONE_2", "+380671234567"));
        assertThat(masked.phone("PHONE_2")).isEqualTo("+380671234567");
        assertThat(masked.phone("[PHONE_1]")).isEqualTo("+380501112233");
        assertThat(masked.phone("PHONE_3")).isNull();
        assertThat(masked.phone(null)).isNull();
    }

    @Test
    void emailsAreMaskedAndNotMapped() {
        String text = "Пишіть olena.p@gmail.com або Olena.P@Gmail.com, чи petro@ukr.net";

        MaskedText masked = mask(text);

        assertThat(masked.text()).isEqualTo("Пишіть [EMAIL_1] або [EMAIL_1], чи [EMAIL_2]");
        assertThat(masked.phones()).isEmpty();
    }

    @Test
    void phonesAndEmailsTogether() {
        String text = "067-123-45-67або olena@ukr.net";

        assertThat(mask(text).text()).isEqualTo("[PHONE_1]або [EMAIL_1]");
    }

    @Test
    void textWithoutContactsStaysAsItIs() {
        String text = "Знайдено ключі біля ратуші 12.10.2026 о 14:30";

        MaskedText masked = mask(text);

        assertThat(masked.text()).isEqualTo(text);
        assertThat(masked.phones()).isEmpty();
    }

    @Test
    void emptyTextGivesEmptyMask() {
        MaskedText masked = masker.mask("", List.of());

        assertThat(masked.text()).isEmpty();
        assertThat(masked.phones()).isEmpty();
    }

    @Test
    void theSameTextAlwaysGivesTheSameReferences() {
        String text = "050 111 22 33 / 067 123 45 67";

        assertThat(mask(text)).isEqualTo(mask(text));
    }

    private MaskedText mask(String text) {
        return masker.mask(text, extractor.find(text, "UA"));
    }
}
