package org.shpytchuk.ingest.image.ocr;

import com.recognition.software.jdeskew.ImageDeskew;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITessAPI.TessPageIteratorLevel;
import net.sourceforge.tess4j.ITessAPI.TessPageSegMode;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import net.sourceforge.tess4j.util.ImageHelper;
import org.shpytchuk.ingest.config.redaction.RedactionProperties;
import org.shpytchuk.ingest.image.Rotations;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class TesseractOcrEngine implements OcrEngine {

    private static final double MIN_DESKEW_DEGREES = 0.5;
    private static final double MAX_DESKEW_DEGREES = 20;
    private static final double CONFIDENT_READING = 120;
    private static final double TURN_ADVANTAGE = 1.5;
    private static final double MIN_TURNED_READING = 20;
    private static final List<Integer> SIDEWAYS_TURNS = List.of(1, 3);
    private static final int TARGET_LINE_HEIGHT = 40;
    private static final double MIN_ENLARGEMENT = 1.25;
    private static final double MAX_ENLARGEMENT = 4;
    private static final int LINE_MIN_CHARACTERS = 3;

    private final Tesseract tesseract = new Tesseract();

    public TesseractOcrEngine(RedactionProperties properties) {
        for (String language : properties.languages().split("\\+")) {
            if (!Files.isRegularFile(Path.of(properties.tessdata(), language + ".traineddata"))) {
                throw new IllegalStateException("Tesseract language data " + language
                        + ".traineddata is missing in " + properties.tessdata());
            }
        }
        tesseract.setDatapath(properties.tessdata());
        tesseract.setLanguage(properties.languages());
        tesseract.setPageSegMode(TessPageSegMode.PSM_AUTO);
        log.info("OCR is on: Tesseract, languages {}", properties.languages());
    }

    @Override
    public synchronized OcrPage read(BufferedImage image) {
        Reading best = enlargedIfShort(image, reading(image, 1.0, 0));
        if (recognized(best.lines()) < CONFIDENT_READING) {
            for (int turns : SIDEWAYS_TURNS) {
                Reading turned = reading(image, best.scale(), turns);
                if (clearlyBetter(turned, best)) {
                    best = turned;
                }
            }
            best = enlargedIfShort(image, best);
        }
        List<OcrLine> lines = deskewed(best);
        log.debug("OCR read {} line(s) at scale {} turned {} quarter(s) clockwise", lines.size(),
                String.format("%.2f", best.scale()), best.quarterTurns());
        return new OcrPage(lines, best.quarterTurns());
    }

    private Reading reading(BufferedImage image, double scale, int quarterTurns) {
        BufferedImage gray = ImageHelper.convertImageToGrayscale(Rotations.quarterTurns(enlarged(image, scale), quarterTurns));
        List<OcrLine> lines = read(gray, AffineTransform.getScaleInstance(scale, scale), bounds(gray, scale));
        return new Reading(gray, quarterTurns, scale, lines);
    }

    private Reading enlargedIfShort(BufferedImage image, Reading best) {
        double enlargement = enlargement(best.lines());
        if (best.scale() != 1.0 || enlargement < MIN_ENLARGEMENT) {
            return best;
        }
        Reading enlarged = reading(image, enlargement, best.quarterTurns());
        return recognized(enlarged.lines()) > recognized(best.lines()) ? enlarged : best;
    }

    private static boolean clearlyBetter(Reading candidate, Reading best) {
        double score = recognized(candidate.lines());
        return score >= MIN_TURNED_READING && score > recognized(best.lines()) * TURN_ADVANTAGE;
    }

    static double enlargement(List<OcrLine> lines) {
        List<Integer> heights = lines.stream()
                .filter(line -> characters(line) >= LINE_MIN_CHARACTERS)
                .map(line -> line.box().height)
                .sorted()
                .toList();
        if (heights.isEmpty()) {
            return 1.0;
        }
        int median = heights.get(heights.size() / 2);
        return median >= TARGET_LINE_HEIGHT ? 1.0 : Math.min(MAX_ENLARGEMENT, TARGET_LINE_HEIGHT / (double) median);
    }

    private static BufferedImage enlarged(BufferedImage image, double scale) {
        if (scale == 1.0) {
            return image;
        }
        int width = (int) Math.round(image.getWidth() * scale);
        int height = (int) Math.round(image.getHeight() * scale);
        BufferedImage enlarged = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = enlarged.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(image, 0, 0, width, height, null);
        g.dispose();
        return enlarged;
    }

    private List<OcrLine> deskewed(Reading reading) {
        BufferedImage gray = reading.gray();
        double skew = new ImageDeskew(gray).getSkewAngle();
        if (Math.abs(skew) <= MIN_DESKEW_DEGREES || Math.abs(skew) > MAX_DESKEW_DEGREES) {
            return reading.lines();
        }
        AffineTransform rotation = rotation(gray, Math.toRadians(-skew));
        AffineTransform toOcr = new AffineTransform(rotation);
        toOcr.scale(reading.scale(), reading.scale());
        List<OcrLine> straightened = read(rotate(gray, rotation), toOcr, bounds(gray, reading.scale()));
        return recognized(straightened) > recognized(reading.lines()) ? straightened : reading.lines();
    }

    private static Rectangle bounds(BufferedImage gray, double scale) {
        return new Rectangle((int) Math.round(gray.getWidth() / scale), (int) Math.round(gray.getHeight() / scale));
    }

    private List<OcrLine> read(BufferedImage gray, AffineTransform toOcr, Rectangle bounds) {
        List<Word> words;
        try {
            words = tesseract.getWords(List.of(gray), TessPageIteratorLevel.RIL_TEXTLINE);
        } catch (LinkageError e) {
            throw new IllegalStateException("Tesseract is not available", e);
        }
        AffineTransform back = inverse(toOcr);
        List<OcrLine> lines = new ArrayList<>(words.size());
        for (Word word : words) {
            String text = word.getText().strip();
            Rectangle box = back.createTransformedShape(word.getBoundingBox()).getBounds().intersection(bounds);
            if (!text.isEmpty() && !box.isEmpty()) {
                lines.add(new OcrLine(text, box, word.getConfidence() / 100.0));
            }
        }
        return lines;
    }

    private static double recognized(List<OcrLine> lines) {
        return lines.stream().mapToDouble(line -> characters(line) * line.confidence()).sum();
    }

    private static long characters(OcrLine line) {
        return line.text().codePoints().filter(Character::isLetterOrDigit).count();
    }

    private static AffineTransform rotation(BufferedImage image, double radians) {
        AffineTransform rotation = AffineTransform.getRotateInstance(radians);
        Rectangle rotated = rotation.createTransformedShape(new Rectangle(image.getWidth(), image.getHeight())).getBounds();
        rotation.preConcatenate(AffineTransform.getTranslateInstance(-rotated.x, -rotated.y));
        return rotation;
    }

    private static BufferedImage rotate(BufferedImage gray, AffineTransform rotation) {
        Rectangle canvas = rotation.createTransformedShape(new Rectangle(gray.getWidth(), gray.getHeight())).getBounds();
        BufferedImage rotated = new BufferedImage(canvas.x + canvas.width, canvas.y + canvas.height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = rotated.createGraphics();
        // white corners: black ones would be read as large dark blobs
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, rotated.getWidth(), rotated.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(gray, rotation, null);
        g.dispose();
        return rotated;
    }

    private static AffineTransform inverse(AffineTransform transform) {
        try {
            return transform.createInverse();
        } catch (NoninvertibleTransformException e) {
            throw new IllegalStateException("a scaled rotation is always invertible", e);
        }
    }

    private record Reading(BufferedImage gray, int quarterTurns, double scale, List<OcrLine> lines) {
    }
}
