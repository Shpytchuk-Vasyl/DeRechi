package org.shpytchuk.ingest.image.redaction;

import org.shpytchuk.ingest.image.ocr.OcrLine;

import java.util.List;
import java.util.regex.Pattern;

public final class PhotoText {

    static final int MAX_LINES = 40;
    static final int MAX_LINE_LENGTH = 80;
    private static final int MIN_LETTERS = 2;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern DIGIT = Pattern.compile("\\p{Nd}");
    private static final Pattern REACHABLE = Pattern.compile("(?i)@|://|\\bwww\\.|\\bt\\.me/");

    private PhotoText() {
    }

    public static List<String> of(List<OcrLine> lines) {
        return lines.stream()
                .map(OcrLine::text)
                .map(text -> WHITESPACE.matcher(text.strip()).replaceAll(" "))
                .filter(PhotoText::isWords)
                .map(PhotoText::cut)
                .distinct()
                .limit(MAX_LINES)
                .toList();
    }

    static boolean isWords(String line) {
        return !DIGIT.matcher(line).find()
                && !REACHABLE.matcher(line).find()
                && line.codePoints().filter(Character::isLetter).count() >= MIN_LETTERS;
    }

    private static String cut(String line) {
        return line.length() <= MAX_LINE_LENGTH ? line : line.substring(0, MAX_LINE_LENGTH).stripTrailing();
    }
}
