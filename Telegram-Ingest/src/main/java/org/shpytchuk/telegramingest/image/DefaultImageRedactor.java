package org.shpytchuk.telegramingest.image;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.telegramingest.config.RedactionProperties;
import org.shpytchuk.telegramingest.config.RedactionProperties.DocumentMode;
import org.shpytchuk.telegramingest.config.TelegramProperties;
import org.shpytchuk.telegramingest.exception.IngestException;
import org.shpytchuk.telegramingest.phone.PhoneExtractor;
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
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

@Component
@AllArgsConstructor
@Slf4j
public class DefaultImageRedactor implements ImageRedactor {

    private static final int MAX_SIDE = 1600;
    private static final float JPEG_QUALITY = 0.88f;
    private static final int PHONE_DIGITS = 9;
    private static final double COUNTED_WORD_CONFIDENCE = 0.5;
    private static final Pattern EMAIL = Pattern.compile("[\\p{L}0-9._%+-]+\\s?@\\s?[\\p{L}0-9-]+(\\.[\\p{L}0-9-]+)*\\.\\p{L}{2,}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]");

    private final RedactionProperties properties;
    private final TelegramProperties telegram;
    private final PhoneExtractor phoneExtractor;
    private final OcrEngine ocr;
    private final FaceDetector faceDetector;
    private final DocumentDetector documentDetector;

    @Override
    public RedactedImage redact(byte[] image, boolean documentHint) {
        BufferedImage picture = decode(image);
        List<OcrLine> lines;
        try {
            lines = ocr.lines(picture);
        } catch (RuntimeException e) {
            log.warn("OCR failed, the photo is blurred entirely: {}", getMostSpecificCause(e).toString());
            return full(picture, "ocr failed");
        }
        if (documentHint || documentDetector.looksLikeDocument(lines)) {
            return document(picture, lines);
        }
        List<OcrLine> contactLines = lines.stream().filter(this::hasContact).toList();
        contactLines.forEach(line -> Blur.region(picture, line.box()));
        RedactionMode mode = contactLines.isEmpty() ? RedactionMode.NONE : RedactionMode.PHONES;
        log.debug("Redacted photo: mode {}, {} lines, {} contact lines", mode, lines.size(), contactLines.size());
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

    private RedactedImage document(BufferedImage picture, List<OcrLine> lines) {
        if (properties.documentMode() == DocumentMode.FULL) {
            return full(picture, "document, full mode");
        }
        long words = countWords(lines);
        if (words < properties.minWords()) {
            return full(picture, "document, %d words".formatted(words));
        }
        List<Rectangle> faces;
        try {
            faces = faceDetector.faces(picture);
        } catch (RuntimeException e) {
            log.warn("Face detection failed, the photo is blurred entirely: {}", getMostSpecificCause(e).toString());
            return full(picture, "face detection failed");
        }
        Stream.concat(lines.stream().map(OcrLine::box), faces.stream()).forEach(box -> Blur.region(picture, box));
        log.debug("Redacted photo: mode {}, {} lines, {} words, {} faces", RedactionMode.DOCUMENT, lines.size(), words, faces.size());
        return new RedactedImage(encode(picture), RedactionMode.DOCUMENT);
    }

    private RedactedImage full(BufferedImage picture, String reason) {
        log.debug("Redacted photo: mode {} ({})", RedactionMode.FULL, reason);
        return new RedactedImage(encode(Blur.whole(picture)), RedactionMode.FULL);
    }

    boolean hasContact(OcrLine line) {
        String text = line.text();
        return text.chars().filter(Character::isDigit).count() >= PHONE_DIGITS
                || EMAIL.matcher(text).find()
                || !phoneExtractor.find(text, telegram.defaultRegion()).isEmpty();
    }

    private static long countWords(List<OcrLine> lines) {
        return lines.stream()
                .filter(line -> line.confidence() >= COUNTED_WORD_CONFIDENCE)
                .flatMap(line -> Arrays.stream(WHITESPACE.split(line.text())))
                .filter(token -> WORD.matcher(token).find())
                .count();
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
