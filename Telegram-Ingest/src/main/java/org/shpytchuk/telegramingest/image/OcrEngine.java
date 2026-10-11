package org.shpytchuk.telegramingest.image;

import java.awt.image.BufferedImage;
import java.util.List;

public interface OcrEngine {

    List<OcrLine> lines(BufferedImage image);
}
