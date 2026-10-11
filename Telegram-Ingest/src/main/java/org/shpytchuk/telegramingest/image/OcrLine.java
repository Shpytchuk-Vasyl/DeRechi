package org.shpytchuk.telegramingest.image;

import java.awt.Rectangle;

public record OcrLine(String text, Rectangle box, double confidence) {
}
