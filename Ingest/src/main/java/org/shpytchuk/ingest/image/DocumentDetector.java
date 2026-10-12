package org.shpytchuk.ingest.image;

import org.shpytchuk.ingest.config.RedactionProperties;
import org.springframework.stereotype.Component;

import java.awt.Rectangle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class DocumentDetector {

    /** ICAO 9303 lines are 30 (TD1), 36 (TD2) or 44 (TD3) characters; OCR may lose a few. */
    private static final int MRZ_MIN_LENGTH = 25;
    private static final Pattern MRZ_CHARS = Pattern.compile("[A-Z0-9<]+");
    private static final List<String> MRZ_PREFIXES = List.of("P<", "ID", "I<", "AC", "C<", "V<");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern APOSTROPHES = Pattern.compile("[’ʼ‘`´′]");
    private static final int MAX_DOCUMENT_FACES = 3;
    private static final double PORTRAIT_MAX_CENTER = 0.4;
    private static final double PORTRAIT_MIN_WIDTH = 0.06;

    private final List<String> keywords;

    public DocumentDetector(RedactionProperties properties) {
        this.keywords = properties.documentKeywords().stream().map(DocumentDetector::normalize).toList();
    }

    public boolean mentionsDocument(String text) {
        String normalized = normalize(text);
        return keywords.stream().anyMatch(normalized::contains);
    }

    public boolean looksLikeDocument(List<OcrLine> lines) {
        List<String> texts = lines.stream().map(OcrLine::text).toList();
        return texts.stream().anyMatch(DocumentDetector::isMrzLine) || mentionsDocument(String.join("\n", texts));
    }

    public boolean portraitOnTheLeft(List<Rectangle> faces, int imageWidth) {
        if (faces.isEmpty() || faces.size() > MAX_DOCUMENT_FACES) {
            return false;
        }
        Rectangle largest = faces.stream().max(Comparator.comparingInt(face -> face.width * face.height)).orElseThrow();
        return largest.width >= imageWidth * PORTRAIT_MIN_WIDTH && largest.getCenterX() < imageWidth * PORTRAIT_MAX_CENTER;
    }

    /**
     * A line of the machine-readable zone: at least {@value #MRZ_MIN_LENGTH} characters of
     * {@code [A-Z0-9<]} once spaces are dropped, with a {@code <<} filler or starting like a
     * passport, ID card or visa line and carrying at least one {@code <}.
     */
    static boolean isMrzLine(String line) {
        String compact = WHITESPACE.matcher(line).replaceAll("")
                .replace("«", "<<").replace("‹", "<")
                .toUpperCase(Locale.ROOT);
        if (compact.length() < MRZ_MIN_LENGTH || !MRZ_CHARS.matcher(compact).matches()) {
            return false;
        }
        return compact.contains("<<")
                || compact.indexOf('<') >= 0 && MRZ_PREFIXES.stream().anyMatch(compact::startsWith);
    }

    private static String normalize(String text) {
        return APOSTROPHES.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("'");
    }
}
