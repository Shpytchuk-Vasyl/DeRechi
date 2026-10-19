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
        BufferedImage gray = ImageHelper.convertImageToGrayscale(image);
        Reading best = new Reading(gray, 0, readAsIs(gray));
        if (isPortrait(gray) && recognized(best.lines()) < CONFIDENT_READING) {
            double asIs = recognized(best.lines());
            for (int turns : SIDEWAYS_TURNS) {
                BufferedImage turned = Rotations.quarterTurns(gray, turns);
                Reading candidate = new Reading(turned, turns, readAsIs(turned));
                double score = recognized(candidate.lines());
                if (score >= MIN_TURNED_READING && score > asIs * TURN_ADVANTAGE && score > recognized(best.lines())) {
                    best = candidate;
                }
            }
            if (best.quarterTurns() != 0) {
                log.debug("OCR reads the photo best turned {} quarter(s) clockwise", best.quarterTurns());
            }
        }
        return new OcrPage(deskewed(best), best.quarterTurns());
    }

    private static boolean isPortrait(BufferedImage image) {
        return image.getHeight() > image.getWidth();
    }

    private List<OcrLine> readAsIs(BufferedImage gray) {
        return read(gray, new AffineTransform(), new Rectangle(gray.getWidth(), gray.getHeight()));
    }

    private List<OcrLine> deskewed(Reading reading) {
        BufferedImage gray = reading.gray();
        double skew = new ImageDeskew(gray).getSkewAngle();
        if (Math.abs(skew) <= MIN_DESKEW_DEGREES || Math.abs(skew) > MAX_DESKEW_DEGREES) {
            return reading.lines();
        }
        AffineTransform rotation = rotation(gray, Math.toRadians(-skew));
        List<OcrLine> straightened = read(rotate(gray, rotation), rotation,
                new Rectangle(gray.getWidth(), gray.getHeight()));
        return recognized(straightened) > recognized(reading.lines()) ? straightened : reading.lines();
    }

    private List<OcrLine> read(BufferedImage gray, AffineTransform rotation, Rectangle bounds) {
        List<Word> words;
        try {
            words = tesseract.getWords(List.of(gray), TessPageIteratorLevel.RIL_TEXTLINE);
        } catch (LinkageError e) {
            throw new IllegalStateException("Tesseract is not available", e);
        }
        AffineTransform back = inverse(rotation);
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
        return lines.stream()
                .mapToDouble(line -> line.text().codePoints().filter(Character::isLetterOrDigit).count() * line.confidence())
                .sum();
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

    private static AffineTransform inverse(AffineTransform rotation) {
        try {
            return rotation.createInverse();
        } catch (NoninvertibleTransformException e) {
            throw new IllegalStateException("a rotation is always invertible", e);
        }
    }

    private record Reading(BufferedImage gray, int quarterTurns, List<OcrLine> lines) {
    }
}
