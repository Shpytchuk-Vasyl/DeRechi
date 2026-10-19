package org.shpytchuk.ingest.image.ocr;

import java.util.List;

/**
 * @param lines        text lines with boxes in the coordinates of the image turned upright
 * @param quarterTurns clockwise quarter turns that put the image upright, 0 to 3
 */
public record OcrPage(List<OcrLine> lines, int quarterTurns) {

    public static OcrPage upright(List<OcrLine> lines) {
        return new OcrPage(lines, 0);
    }

    public static OcrPage empty() {
        return upright(List.of());
    }
}
