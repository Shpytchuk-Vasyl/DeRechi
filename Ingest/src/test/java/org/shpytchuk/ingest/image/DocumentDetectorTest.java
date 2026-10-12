package org.shpytchuk.ingest.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.shpytchuk.ingest.config.RedactionProperties;

import java.awt.Rectangle;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentDetectorTest {

    static final List<String> KEYWORDS = List.of("паспорт", "passport", "посвідчення", "посвідка", "свідоцтво",
            "водійськ", "id-картк", "айді", "ідентифікац", "леокарт", "студентськ", "пенсійн", "військов",
            "dowód", "legitymacja", "paszport", "personalausweis", "reisepass", "führerschein",
            "carte d'identité", "permis de conduire", "обʼєднан");

    private final DocumentDetector detector = new DocumentDetector(properties(KEYWORDS));

    @ParameterizedTest
    @ValueSource(strings = {
            "P<UKRSHEVCHENKO<<TARAS<<<<<<<<<<<<<<<<<<<<<",
            "FE123456<7UKR8001014M3001012<<<<<<<<<<<<<<06",
            "IDUKR0000000001<<<<<<<<<<<<<<<",
            "I<POLABC123456<<<<<<<<<<<<<<<<",
            "P< UKR SHEVCHENKO << TARAS <<<<<<<<<<<<",
            "IDD<<T220001293<<<<<<<<<<<<<<<",
            "8001014M3001012UKR«««««««««6"
    })
    void recognizesMachineReadableZoneLines(String line) {
        assertThat(DocumentDetector.isMrzLine(line)).isTrue();
        assertThat(detector.looksLikeDocument(List.of(line(line)))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "LOST DOG REWARD CALL NOW 0671234567",
            "ACCESSORIES AND BAGS FOR SALE 2024",
            "P<UKR",
            "Знайдено гаманець біля вокзалу <<",
            "1234567890123456789012345678901"
    })
    void ordinaryLinesAreNotMachineReadable(String line) {
        assertThat(DocumentDetector.isMrzLine(line)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Знайдено ПАСПОРТ на імʼя Тараса",
            "Загубила закордонний паспорт, прошу повернути",
            "знайшли ID-картку біля метро",
            "Знайдено посвідчення водія",
            "Zgubiłem dowód osobisty w tramwaju",
            "Zgubiona legitymacja studencka",
            "Personalausweis am Bahnhof gefunden",
            "Habe meinen FÜHRERSCHEIN verloren",
            "J'ai perdu ma carte d’identité",
            "Знайдено документи ОСББ «Обʼєднання»",
            "Знайдено документи ОСББ «Об’єднання»",
            "Знайдено документи ОСББ «Об'єднання»"
    })
    void recognizesDocumentKeywords(String text) {
        assertThat(detector.mentionsDocument(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Знайдено гаманець біля вокзалу, тел. 067 123 45 67",
            "Загубився кіт, рудий, відгукується на Барсик",
            "Zgubiłem klucze na Rynku",
            "Schlüssel am Bahnhof gefunden",
            ""
    })
    void ordinaryTextIsNotADocument(String text) {
        assertThat(detector.mentionsDocument(text)).isFalse();
        assertThat(detector.looksLikeDocument(List.of(line(text)))).isFalse();
    }

    @Test
    void keywordsAreFoundAcrossOcrLines() {
        List<OcrLine> lines = List.of(line("УКРАЇНА"), line("ПАСПОРТ ГРОМАДЯНИНА УКРАЇНИ"), line("Прізвище"));

        assertThat(detector.looksLikeDocument(lines)).isTrue();
    }

    @Test
    void withoutKeywordsOnlyTheMachineReadableZoneCounts() {
        DocumentDetector bare = new DocumentDetector(properties(List.of()));

        assertThat(bare.mentionsDocument("паспорт")).isFalse();
        assertThat(bare.looksLikeDocument(List.of(line("P<UKRSHEVCHENKO<<TARAS<<<<<<<<<<<<<<<<<<<<<")))).isTrue();
    }

    @Test
    void largestFaceOnTheLeftLooksLikeADocumentPortrait() {
        List<Rectangle> mainPhotoAndGhost = List.of(new Rectangle(60, 200, 150, 190), new Rectangle(820, 400, 50, 60));

        assertThat(detector.portraitOnTheLeft(mainPhotoAndGhost, 1000)).isTrue();
    }

    @Test
    void faceInTheMiddleOrOnTheRightIsNotAPortrait() {
        assertThat(detector.portraitOnTheLeft(List.of(new Rectangle(420, 200, 160, 200)), 1000)).isFalse();
        assertThat(detector.portraitOnTheLeft(List.of(new Rectangle(700, 200, 160, 200)), 1000)).isFalse();
    }

    @Test
    void tinyFaceOrAGroupIsNotAPortrait() {
        Rectangle left = new Rectangle(60, 200, 150, 190);

        assertThat(detector.portraitOnTheLeft(List.of(new Rectangle(60, 200, 40, 50)), 1000)).isFalse();
        assertThat(detector.portraitOnTheLeft(List.of(left, left, left, left), 1000)).isFalse();
        assertThat(detector.portraitOnTheLeft(List.of(), 1000)).isFalse();
    }

    static OcrLine line(String text) {
        return new OcrLine(text, new Rectangle(0, 0, 10, 10), 0.9);
    }

    static RedactionProperties properties(List<String> keywords) {
        return new RedactionProperties("", null, keywords, null, 0, null);
    }
}
