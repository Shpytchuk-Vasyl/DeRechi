package org.shpytchuk.ingest.image.ocr;

import java.awt.image.BufferedImage;

public interface OcrEngine {

    OcrPage read(BufferedImage image);
}
