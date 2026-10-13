package org.shpytchuk.ingest.image.face;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

public interface FaceDetector {

    List<Rectangle> faces(BufferedImage image);
}
