package org.shpytchuk.ingest.image.ocr;

import java.awt.image.BufferedImage;
import java.util.List;

public interface OcrEngine {

    List<OcrLine> lines(BufferedImage image);
}
