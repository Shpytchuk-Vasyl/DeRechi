package org.shpytchuk.ingest.image.ocr;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TesseractOcrEngineTest {

    @Test
    void smallPrintIsEnlargedUntilTheMedianLineIsFortyPixelsTall() {
        List<OcrLine> small = List.of(line("ДЛЯ РЕЄСТРАЦІЇ ПРОЇЗДУ", 14), line("Аліна Сурмай", 12), line("ПРИКЛАДІТЬ", 16));

        assertThat(TesseractOcrEngine.enlargement(small)).isCloseTo(40 / 14.0, within(0.001));
    }

    @Test
    void largePrintIsNotEnlarged() {
        List<OcrLine> large = List.of(line("КЛИМОВА", 70), line("ТЕТЯНА", 64), line("ЛЕОНІДІВНА", 66));

        assertThat(TesseractOcrEngine.enlargement(large)).isEqualTo(1.0);
    }

    @Test
    void enlargementIsCappedAtFourAndIgnoresNoiseAndEmptyReadings() {
        assertThat(TesseractOcrEngine.enlargement(List.of(line("ШЕВЧЕНКО", 5)))).isEqualTo(4.0);
        assertThat(TesseractOcrEngine.enlargement(List.of(line("|", 4), line("ТАРАС", 50)))).isEqualTo(1.0);
        assertThat(TesseractOcrEngine.enlargement(List.of())).isEqualTo(1.0);
    }

    private static OcrLine line(String text, int height) {
        return new OcrLine(text, new Rectangle(0, 0, 200, height), 0.9);
    }
}
