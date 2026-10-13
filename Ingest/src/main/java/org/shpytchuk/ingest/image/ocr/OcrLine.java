package org.shpytchuk.ingest.image.ocr;

import java.awt.Rectangle;

public record OcrLine(String text, Rectangle box, double confidence) {
}
