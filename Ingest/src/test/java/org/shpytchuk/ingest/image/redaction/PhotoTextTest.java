package org.shpytchuk.ingest.image.redaction;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.image.ocr.OcrLine;

import java.awt.Rectangle;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** What may leave the machine for the owner's name lookup: words only, nothing that identifies or reaches a person. */
class PhotoTextTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "ПАСПОРТ ГРОМАДЯНИНА УКРАЇНИ",
            "ПРІЗВИЩЕ / SURNAME",
            "ШЕВЧЕНКО",
            "SHEVCHENKO",
            "ІМ'Я / GIVEN NAMES",
            "Тарас Григорович",
            "P<UKRSHEVCHENKO<<TARAS<<<<<<<<<<<<<<<<<<<<<<",
            "МІСЦЕ НАРОДЖЕННЯ ЛЬВІВ",
            "Dowód osobisty",
            "KOWALSKI",
            "ЛЕОКАРТА",
            "ПОСВІДЧЕННЯ ВОДІЯ",
    })
    void keepsLinesOfWords(String line) {
        assertThat(PhotoText.of(List.of(line(line)))).containsExactly(line);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // phone numbers, in every spelling OCR produces
            "+380 67 123 45 67",
            "тел. 0671234567",
            "067-123-45-67",
            "Тел: +48 501 234 567",
            // document numbers and series
            "AB 123456",
            "№ 123456",
            "ЕН345678",
            "Паспорт № 123456",
            "ID 001234567",
            "Запис № 19900101-01234",
            // the second MRZ line of a passport and the first of an ID card carry the document number
            "1234567890UKR9001011M2501012<<<<<<<<<<<<<<04",
            "IDUKR001234567<<<<<<<<<<<<<<<<",
            // dates, ages, years
            "12.08.1964",
            "Дата народження 12.08.1964",
            "Дата видачі 01.02.2020",
            "Дійсний до 01.02.2030",
            "1964",
            // card, account and tax numbers
            "4111 1111 1111 1234",
            "4111 1111",
            "UA21 3223 1300 0002 6007 2335 6600 1",
            "РНОКПП 1234567890",
            "ІПН 3012345678",
            // addresses
            "вул. Наукова, 12, кв. 34",
            "м. Львів, вул. Шевченка, 7",
            "79000",
            "31-042 Kraków",
            // issuing authority codes
            "Орган, що видав 4612",
            "Код підрозділу 4612",
            // anything a person can be reached at
            "ivan.petrenko@gmail.com",
            "пишіть ivan @ gmail . com",
            "@ivan_petrenko",
            "t.me/ivan_petrenko",
            "https://facebook.com/ivan.petrenko",
            "www.example.com",
            // symbols, fillers and single letters are no words
            "<<<<<<<<<<<<<<<<<<<<<<<<",
            "- - - - -",
            "A",
            "м.",
            "",
            "   ",
    })
    void dropsLinesThatIdentifyOrReachAPerson(String line) {
        assertThat(PhotoText.of(List.of(line(line)))).isEmpty();
    }

    @Test
    void aDigitAnywhereInTheLineDropsTheWholeLineNotJustTheNumber() {
        List<String> text = PhotoText.of(List.of(line("ШЕВЧЕНКО ТАРАС 12.08.1964"), line("ШЕВЧЕНКО ТАРАС")));

        assertThat(text).containsExactly("ШЕВЧЕНКО ТАРАС");
    }

    @Test
    void digitsOfOtherScriptsCountAsDigits() {
        assertThat(PhotoText.of(List.of(line("رقم ١٢٣٤٥٦"), line("номер १२३४५६")))).isEmpty();
    }

    @Test
    void collapsesWhitespaceAndDropsRepeats() {
        List<String> text = PhotoText.of(List.of(line("  ШЕВЧЕНКО \t ТАРАС  "), line("ШЕВЧЕНКО ТАРАС"),
                line("ШЕВЧЕНКО\nТАРАС")));

        assertThat(text).containsExactly("ШЕВЧЕНКО ТАРАС");
    }

    @Test
    void cutsALineToEightyCharactersAndKeepsAtMostFortyLines() {
        String longLine = "СЛОВО ".repeat(30).strip();
        List<OcrLine> many = IntStream.rangeClosed(1, 60).mapToObj(i -> line("Рядок " + "і".repeat(i))).toList();

        List<String> cut = PhotoText.of(List.of(line(longLine)));
        List<String> capped = PhotoText.of(many);

        assertThat(cut).singleElement().satisfies(text -> {
            assertThat(text).hasSizeLessThanOrEqualTo(PhotoText.MAX_LINE_LENGTH);
            assertThat(text).doesNotEndWith(" ");
            assertThat(longLine).startsWith(text);
        });
        assertThat(capped).hasSize(PhotoText.MAX_LINES).startsWith("Рядок і");
    }

    @Test
    void droppedLinesForTheLogCarryNoDigitsHandlesOrLinks() {
        List<String> dropped = PhotoText.dropped(List.of(line("ШЕВЧЕНКО"), line("Тел. +380 67 123 45 67"),
                line("AB 123456"), line("пишіть ivan.petrenko@gmail.com або @ivan_p"), line("https://t.me/ivan_p"),
                line("<<<<")));

        assertThat(dropped).containsExactly("Тел. +### ## ### ## ##", "AB ######", "пишіть [contact] або [contact]",
                "[contact]", "<<<<");
        assertThat(String.join("\n", dropped)).doesNotContainPattern("\\d").doesNotContain("@", "gmail", "t.me");
    }

    @Test
    void keepsTheOrderOfTheLinesOnThePhoto() {
        List<String> text = PhotoText.of(List.of(line("ПРІЗВИЩЕ"), line("ШЕВЧЕНКО"), line("ІМ'Я"), line("ТАРАС")));

        assertThat(text).containsExactly("ПРІЗВИЩЕ", "ШЕВЧЕНКО", "ІМ'Я", "ТАРАС");
    }

    private static OcrLine line(String text) {
        return new OcrLine(text, new Rectangle(0, 0, 10, 10), 0.9);
    }
}
