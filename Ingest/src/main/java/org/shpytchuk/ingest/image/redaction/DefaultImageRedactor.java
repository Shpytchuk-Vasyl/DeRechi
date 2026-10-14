package org.shpytchuk.ingest.image.redaction;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.ingest.config.redaction.RedactionProperties;
import org.shpytchuk.ingest.config.redaction.RedactionProperties.DocumentMode;
import org.shpytchuk.ingest.config.telegram.TelegramProperties;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.image.ImageRedactor;
import org.shpytchuk.ingest.image.RedactedImage;
import org.shpytchuk.ingest.image.RedactionMode;
import org.shpytchuk.ingest.image.face.FaceDetector;
import org.shpytchuk.ingest.image.ocr.OcrEngine;
import org.shpytchuk.ingest.image.ocr.OcrLine;
import org.shpytchuk.ingest.phone.PhoneExtractor;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

@Component
@AllArgsConstructor
@Slf4j
public class DefaultImageRedactor implements ImageRedactor {

    private static final int MAX_SIDE = 1600;
    private static final float JPEG_QUALITY = 0.88f;
    private static final int PHONE_DIGITS = 9;
    private static final int CARD_VISIBLE_DIGITS = 4;
    private static final Pattern EMAIL = Pattern.compile("[\\p{L}0-9._%+-]+\\s?@\\s?[\\p{L}0-9-]+(\\.[\\p{L}0-9-]+)*\\.\\p{L}{2,}");
    private static final Pattern CARD_NUMBER = Pattern.compile(
            "(?<!\\d)(\\d{4}(?:[ -]?\\d{4}){2}[ -]?\\d{1,7}|\\d{4}[ -]?\\d{6}[ -]?\\d{5})(?!\\d)");
    private static final Pattern CARD_FRAGMENT = Pattern.compile("(?<!\\d)\\d{4}[ -]\\d{4}(?!\\d)");

    private final RedactionProperties properties;
    private final TelegramProperties telegram;
    private final PhoneExtractor phoneExtractor;
    private final OcrEngine ocr;
    private final FaceDetector faceDetector;
    private final DocumentDetector documentDetector;

    @Override
    public RedactedImage redact(byte[] image, boolean documentHint) {
        BufferedImage picture = decode(image);
        List<OcrLine> lines = lines(picture);
        List<Rectangle> faces = faces(picture);
        if (documentHint || documentDetector.looksLikeDocument(lines)
                || documentDetector.hasPrintedText(lines)
                && documentDetector.portraitOnTheLeft(faces, picture.getWidth())) {
            return document(picture, faces);
        }
        int hidden = 0;
        for (OcrLine line : lines) {
            if (hideCardNumber(picture, line) || hideContact(picture, line)) {
                hidden++;
            }
        }
        RedactionMode mode = hidden == 0 ? RedactionMode.NONE : RedactionMode.PHONES;
        log.debug("Redacted photo: mode {}, {} lines, {} hidden", mode, lines.size(), hidden);
        return new RedactedImage(encode(picture), mode);
    }

    @Override
    public RedactedImage blurFully(byte[] image) {
        return full(decode(image), "requested");
    }

    @Override
    public boolean mentionsDocument(String text) {
        return documentDetector.mentionsDocument(text);
    }

    private List<OcrLine> lines(BufferedImage picture) {
        try {
            return ocr.lines(picture);
        } catch (RuntimeException e) {
            log.warn("OCR failed, contacts on the photo are not blurred: {}", getMostSpecificCause(e).toString());
            return List.of();
        }
    }

    private List<Rectangle> faces(BufferedImage picture) {
        try {
            return faceDetector.faces(picture);
        } catch (RuntimeException e) {
            log.warn("Face detection failed, a document photo is blurred entirely: {}", getMostSpecificCause(e).toString());
            return List.of();
        }
    }

    private RedactedImage document(BufferedImage picture, List<Rectangle> faces) {
        if (properties.documentMode() == DocumentMode.FULL) {
            return full(picture, "document, full mode");
        }
        if (faces.isEmpty()) {
            return full(picture, "document without a face");
        }
        log.debug("Redacted photo: mode {}, {} faces kept", RedactionMode.DOCUMENT, faces.size());
        return new RedactedImage(encode(Blur.allBut(picture, faces)), RedactionMode.DOCUMENT);
    }

    private RedactedImage full(BufferedImage picture, String reason) {
        log.debug("Redacted photo: mode {} ({})", RedactionMode.FULL, reason);
        return new RedactedImage(encode(Blur.whole(picture)), RedactionMode.FULL);
    }

    private boolean hideCardNumber(BufferedImage picture, OcrLine line) {
        Matcher card = CARD_NUMBER.matcher(line.text());
        if (!card.find()) {
            return false;
        }
        Rectangle box = line.box();
        double characterWidth = box.width / (double) line.text().length();
        int hiddenWidth = (int) Math.round(firstVisibleDigit(line.text(), card.end()) * characterWidth);
        Blur.regionUpToRightEdge(picture, new Rectangle(box.x, box.y, hiddenWidth, box.height));
        return true;
    }

    private static int firstVisibleDigit(String text, int end) {
        int digits = 0;
        int index = end;
        while (digits < CARD_VISIBLE_DIGITS) {
            index--;
            if (Character.isDigit(text.charAt(index))) {
                digits++;
            }
        }
        return index;
    }

    private boolean hideContact(BufferedImage picture, OcrLine line) {
        if (!hasContact(line)) {
            return false;
        }
        Blur.region(picture, line.box());
        return true;
    }

    boolean hasContact(OcrLine line) {
        String text = line.text();
        return text.chars().filter(Character::isDigit).count() >= PHONE_DIGITS
                || EMAIL.matcher(text).find()
                || CARD_FRAGMENT.matcher(text).find()
                || !phoneExtractor.find(text, telegram.defaultRegion()).isEmpty();
    }

    static BufferedImage decode(byte[] image) {
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(image));
        } catch (IOException e) {
            throw new IngestException("unreadable image: " + getMostSpecificCause(e), e);
        }
        if (source == null) {
            throw new IngestException("unsupported image format");
        }
        double scale = Math.min(1.0, MAX_SIDE / (double) Math.max(source.getWidth(), source.getHeight()));
        int width = (int) Math.round(source.getWidth() * scale);
        int height = (int) Math.round(source.getHeight() * scale);
        BufferedImage picture = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = picture.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, width, height, Color.WHITE, null);
        g.dispose();
        return picture;
    }

    private static byte[] encode(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException("JPEG encoding failed", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
