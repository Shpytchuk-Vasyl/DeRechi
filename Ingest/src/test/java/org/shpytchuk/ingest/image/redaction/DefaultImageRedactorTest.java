package org.shpytchuk.ingest.image.redaction;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.config.redaction.RedactionProperties;
import org.shpytchuk.ingest.config.redaction.RedactionProperties.DocumentMode;
import org.shpytchuk.ingest.config.telegram.TelegramProperties;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.image.RedactedImage;
import org.shpytchuk.ingest.image.RedactionMode;
import org.shpytchuk.ingest.image.face.FaceDetector;
import org.shpytchuk.ingest.image.ocr.OcrEngine;
import org.shpytchuk.ingest.image.ocr.OcrLine;
import org.shpytchuk.ingest.phone.PhoneExtractor;
import org.shpytchuk.ingest.support.Images;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultImageRedactorTest {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final double SHARP = 5000;
    private static final double BLURRED = 500;

    private static final Rectangle PHONE_BOX = new Rectangle(100, 100, 300, 40);
    private static final Rectangle TEXT_BOX = new Rectangle(100, 300, 300, 40);
    private static final Rectangle FACE_BOX = new Rectangle(550, 150, 120, 150);
    private static final Rectangle PORTRAIT_BOX = new Rectangle(80, 150, 120, 150);
    private static final Rectangle CARD_BOX = new Rectangle(100, 200, 380, 40);
    private static final Rectangle UNTOUCHED = new Rectangle(500, 420, 250, 120);

    private final FakeFaces faces = new FakeFaces();

    @Test
    void blursLinesWithPhoneNumbers() {
        RedactedImage result = redactor(ocr(line("Тел. 067 123 45 67", PHONE_BOX), line("Знайдено гаманець", TEXT_BOX)))
                .redact(stripes(), false);

        assertThat(result.mode()).isEqualTo(RedactionMode.PHONES);
        BufferedImage out = decode(result);
        assertThat(Images.variance(out, PHONE_BOX)).isLessThan(BLURRED);
        assertThat(Images.variance(out, TEXT_BOX)).isGreaterThan(SHARP);
        assertThat(Images.variance(out, UNTOUCHED)).isGreaterThan(SHARP);
    }

    @Test
    void nineDigitsOnALineCountAsAPhoneEvenIfInvalid() {
        RedactedImage result = redactor(ocr(line("дзвоніть 12 34 56 789", PHONE_BOX))).redact(stripes(), false);

        assertThat(result.mode()).isEqualTo(RedactionMode.PHONES);
        assertThat(Images.variance(decode(result), PHONE_BOX)).isLessThan(BLURRED);
    }

    @Test
    void blursLinesWithEmails() {
        RedactedImage result = redactor(ocr(line("пишіть: owner.name@gmail.com", PHONE_BOX))).redact(stripes(), false);

        assertThat(result.mode()).isEqualTo(RedactionMode.PHONES);
        assertThat(Images.variance(decode(result), PHONE_BOX)).isLessThan(BLURRED);
    }

    @Test
    void ordinaryTextIsLeftAloneButReencoded() {
        RedactedImage result = redactor(ocr(line("Знайдено гаманець 2024", TEXT_BOX))).redact(stripes(), false);

        assertThat(result.mode()).isEqualTo(RedactionMode.NONE);
        assertThat(result.jpeg()[0]).isEqualTo((byte) 0xFF);
        assertThat(result.jpeg()[1]).isEqualTo((byte) 0xD8);
        assertThat(hasExif(result.jpeg())).isFalse();
        assertThat(Images.variance(decode(result), TEXT_BOX)).isGreaterThan(SHARP);
    }

    @Test
    void cardNumberKeepsOnlyTheLastFourDigits() {
        RedactedImage result = redactor(ocr(line("4111 1111 1111 1234", CARD_BOX))).redact(stripes(), false);

        assertThat(result.mode()).isEqualTo(RedactionMode.PHONES);
        BufferedImage out = decode(result);
        assertThat(Images.variance(out, new Rectangle(100, 200, 280, 40))).isLessThan(BLURRED);
        assertThat(Images.variance(out, new Rectangle(405, 200, 75, 40))).isGreaterThan(SHARP);
    }

    @Test
    void misreadCardNumberIsBlurredWhole() {
        RedactedImage result = redactor(ocr(line("4111 1111 ТОР", CARD_BOX))).redact(stripes(), false);

        assertThat(result.mode()).isEqualTo(RedactionMode.PHONES);
        assertThat(Images.variance(decode(result), CARD_BOX)).isLessThan(BLURRED);
    }

    @Test
    void documentNamedInThePostIsBlurredExceptTheFace() {
        faces.found = List.of(FACE_BOX);

        RedactedImage result = redactor(ocr(documentLines())).redact(stripes(), true);

        assertThat(result.mode()).isEqualTo(RedactionMode.DOCUMENT);
        BufferedImage out = decode(result);
        assertThat(Images.variance(out, PHONE_BOX)).isLessThan(BLURRED);
        assertThat(Images.variance(out, TEXT_BOX)).isLessThan(BLURRED);
        assertThat(Images.variance(out, UNTOUCHED)).isLessThan(BLURRED);
        assertThat(Images.variance(out, FACE_BOX)).isGreaterThan(SHARP);
    }

    @Test
    void portraitOnTheLeftMakesADocument() {
        faces.found = List.of(PORTRAIT_BOX);

        RedactedImage result = redactor(ocr(line("Mustermann Erika 12.08.64", TEXT_BOX), line("Geburtsort Berlin", PHONE_BOX),
                line("Gültig bis 2030", CARD_BOX))).redact(stripes(), false);

        assertThat(result.mode()).isEqualTo(RedactionMode.DOCUMENT);
        BufferedImage out = decode(result);
        assertThat(Images.variance(out, UNTOUCHED)).isLessThan(BLURRED);
        assertThat(Images.variance(out, PORTRAIT_BOX)).isGreaterThan(SHARP);
    }

    @Test
    void portraitOnTheLeftWithoutPrintedTextIsAnOrdinaryPhoto() {
        faces.found = List.of(PORTRAIT_BOX);

        RedactedImage result = redactor(ocr()).redact(stripes(), false);

        assertThat(result.mode()).isEqualTo(RedactionMode.NONE);
        assertThat(Images.variance(decode(result), UNTOUCHED)).isGreaterThan(SHARP);
    }

    @Test
    void faceOnTheRightIsAnOrdinaryPhoto() {
        faces.found = List.of(FACE_BOX);

        RedactedImage result = redactor(ocr(line("Знайдено гаманець", TEXT_BOX))).redact(stripes(), false);

        assertThat(result.mode()).isEqualTo(RedactionMode.NONE);
        assertThat(Images.variance(decode(result), FACE_BOX)).isGreaterThan(SHARP);
    }

    @Test
    void documentRecognizedByOcrKeyword() {
        faces.found = List.of(FACE_BOX);
        List<OcrLine> lines = List.of(line("ПАСПОРТ ГРОМАДЯНИНА УКРАЇНИ", PHONE_BOX),
                line("Прізвище Імʼя По батькові Дата", TEXT_BOX));

        RedactedImage result = redactor(ocr(lines)).redact(stripes(), false);

        assertThat(result.mode()).isEqualTo(RedactionMode.DOCUMENT);
    }

    @Test
    void documentRecognizedByMachineReadableZone() {
        faces.found = List.of(FACE_BOX);
        List<OcrLine> lines = List.of(line("P<UKRSHEVCHENKO<<TARAS<<<<<<<<<<<<<<<<<<<<<", PHONE_BOX),
                line("one two three four five six", TEXT_BOX));

        assertThat(redactor(ocr(lines)).redact(stripes(), false).mode()).isEqualTo(RedactionMode.DOCUMENT);
    }

    @Test
    void documentWithoutAFaceIsBlurredEntirely() {
        RedactedImage result = redactor(ocr(line("ПАСПОРТ", TEXT_BOX))).redact(stripes(), false);

        assertThat(result.mode()).isEqualTo(RedactionMode.FULL);
        assertWhollyBlurred(result);
    }

    @Test
    void fullDocumentModeBlursEveryDocumentEntirely() {
        faces.found = List.of(FACE_BOX);

        RedactedImage result = redactor(ocr(documentLines()), DocumentMode.FULL).redact(stripes(), true);

        assertThat(result.mode()).isEqualTo(RedactionMode.FULL);
        assertWhollyBlurred(result);
    }

    @Test
    void withoutOcrADocumentIsBlurredEntirely() {
        RedactedImage result = redactor(ocr()).redact(stripes(), true);

        assertThat(result.mode()).isEqualTo(RedactionMode.FULL);
        assertWhollyBlurred(result);
    }

    @Test
    void withoutOcrAnOrdinaryPhotoIsLeftAlone() {
        assertThat(redactor(ocr()).redact(stripes(), false).mode()).isEqualTo(RedactionMode.NONE);
    }

    @Test
    void failedFaceDetectionLeavesAnOrdinaryPhotoToOcr() {
        faces.failure = new IllegalStateException("face model is not loaded");

        RedactedImage result = redactor(ocr(line("Тел. 067 123 45 67", PHONE_BOX), line("Знайдено гаманець", TEXT_BOX)))
                .redact(stripes(), false);

        assertThat(result.mode()).isEqualTo(RedactionMode.PHONES);
        BufferedImage out = decode(result);
        assertThat(Images.variance(out, PHONE_BOX)).isLessThan(BLURRED);
        assertThat(Images.variance(out, TEXT_BOX)).isGreaterThan(SHARP);
    }

    @Test
    void failedFaceDetectionBlursADocumentEntirely() {
        faces.failure = new IllegalStateException("face model is not loaded");

        RedactedImage result = redactor(ocr(documentLines())).redact(stripes(), true);

        assertThat(result.mode()).isEqualTo(RedactionMode.FULL);
        assertWhollyBlurred(result);
    }

    @Test
    void failedOcrLeavesAnOrdinaryPhotoAlone() {
        RedactedImage result = redactor(brokenOcr()).redact(stripes(), false);

        assertThat(result.mode()).isEqualTo(RedactionMode.NONE);
        assertThat(Images.variance(decode(result), UNTOUCHED)).isGreaterThan(SHARP);
    }

    @Test
    void failedOcrStillBlursADocumentExceptTheFace() {
        faces.found = List.of(FACE_BOX);

        RedactedImage result = redactor(brokenOcr()).redact(stripes(), true);

        assertThat(result.mode()).isEqualTo(RedactionMode.DOCUMENT);
        BufferedImage out = decode(result);
        assertThat(Images.variance(out, UNTOUCHED)).isLessThan(BLURRED);
        assertThat(Images.variance(out, FACE_BOX)).isGreaterThan(SHARP);
    }

    private static OcrEngine brokenOcr() {
        return image -> {
            throw new IllegalStateException("Tesseract failed");
        };
    }

    @Test
    void blurFullyBlursEverything() {
        RedactedImage result = redactor(ocr()).blurFully(stripes());

        assertThat(result.mode()).isEqualTo(RedactionMode.FULL);
        assertWhollyBlurred(result);
    }

    @Test
    void largePhotosAreScaledDown() {
        RedactedImage result = redactor(ocr()).redact(png(new BufferedImage(2560, 1920, BufferedImage.TYPE_INT_RGB)), false);

        BufferedImage out = decode(result);
        assertThat(out.getWidth()).isEqualTo(1600);
        assertThat(out.getHeight()).isEqualTo(1200);
    }

    @Test
    void unknownFormatIsRejected() {
        byte[] notAnImage = "definitely not an image".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> redactor(ocr()).redact(notAnImage, false))
                .isInstanceOf(IngestException.class)
                .hasMessage("unsupported image format");
    }

    @Test
    void mentionsDocumentUsesTheKeywords() {
        DefaultImageRedactor redactor = redactor(ocr());

        assertThat(redactor.mentionsDocument("Знайдено паспорт")).isTrue();
        assertThat(redactor.mentionsDocument("Знайдено гаманець")).isFalse();
    }

    private DefaultImageRedactor redactor(OcrEngine ocr) {
        return redactor(ocr, DocumentMode.PARTIAL);
    }

    private DefaultImageRedactor redactor(OcrEngine ocr, DocumentMode mode) {
        RedactionProperties properties = new RedactionProperties("/tessdata", null,
                DocumentDetectorTest.KEYWORDS, null, 0, mode);
        TelegramProperties telegram = new TelegramProperties(0, null, null, null, null, null,
                "UA", "uk", null, null, null, 0);
        return new DefaultImageRedactor(properties, telegram, new PhoneExtractor(), ocr, faces,
                new DocumentDetector(properties));
    }

    private static List<OcrLine> documentLines() {
        return List.of(line("УКРАЇНА / UKRAINE 1234", PHONE_BOX), line("Прізвище Surname Імʼя Given names", TEXT_BOX));
    }

    private static OcrEngine ocr(OcrLine... lines) {
        return ocr(List.of(lines));
    }

    private static OcrEngine ocr(List<OcrLine> lines) {
        return image -> lines;
    }

    private static OcrLine line(String text, Rectangle box) {
        return new OcrLine(text, box, 0.9);
    }

    private static void assertWhollyBlurred(RedactedImage result) {
        BufferedImage out = decode(result);
        for (Rectangle area : List.of(PHONE_BOX, TEXT_BOX, FACE_BOX, UNTOUCHED)) {
            assertThat(Images.variance(out, area)).as("variance of %s", area).isLessThan(BLURRED);
        }
    }

    private static byte[] stripes() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                image.setRGB(x, y, x % 4 < 2 ? 0x000000 : 0xFFFFFF);
            }
        }
        return png(image);
    }

    private static byte[] png(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BufferedImage decode(RedactedImage result) {
        try {
            return ImageIO.read(new ByteArrayInputStream(result.jpeg()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean hasExif(byte[] jpeg) {
        int i = 2;
        while (i + 4 <= jpeg.length && (jpeg[i] & 0xFF) == 0xFF) {
            int marker = jpeg[i + 1] & 0xFF;
            if (marker == 0xE1) {
                return true;
            }
            if (marker == 0xDA) {
                return false;
            }
            i += 2 + ((jpeg[i + 2] & 0xFF) << 8 | jpeg[i + 3] & 0xFF);
        }
        return false;
    }

    private static final class FakeFaces implements FaceDetector {
        private List<Rectangle> found = List.of();
        private @Nullable RuntimeException failure;

        @Override
        public List<Rectangle> faces(BufferedImage image) {
            if (failure != null) {
                throw failure;
            }
            return found;
        }
    }
}
