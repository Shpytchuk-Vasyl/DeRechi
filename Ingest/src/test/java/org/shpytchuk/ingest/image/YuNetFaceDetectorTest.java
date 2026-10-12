package org.shpytchuk.ingest.image;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.config.RedactionProperties;
import org.shpytchuk.ingest.image.YuNetFaceDetector.Candidate;
import org.springframework.core.io.DefaultResourceLoader;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YuNetFaceDetectorTest {

    private YuNetFaceDetector detector;

    @AfterEach
    void close() throws Exception {
        if (detector != null) {
            detector.close();
        }
    }

    @Test
    void bundledModelLoadsAndRuns() {
        detector = detector(null);

        List<Rectangle> faces = detector.faces(BlurTest.noise(640, 480, 7));
        Rectangle bounds = new Rectangle(0, 0, 640, 480);
        assertThat(faces).allSatisfy(face -> assertThat(bounds.contains(face)).isTrue());
    }

    @Test
    void runsOnPhotosOfAnyShape() {
        detector = detector(null);

        assertThat(detector.faces(new BufferedImage(1600, 300, BufferedImage.TYPE_INT_RGB))).isEmpty();
        assertThat(detector.faces(new BufferedImage(200, 900, BufferedImage.TYPE_INT_RGB))).isEmpty();
        assertThat(detector.faces(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB))).isEmpty();
    }

    @Test
    void missingModelMakesEveryDetectionFail() {
        detector = detector("classpath:models/no-such-model.onnx");

        assertThatThrownBy(() -> detector.faces(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nmsKeepsTheBestOfOverlappingBoxes() {
        Candidate best = new Candidate(new Rectangle2D.Double(100, 100, 50, 50), 0.95);
        Candidate overlapping = new Candidate(new Rectangle2D.Double(105, 105, 50, 50), 0.8);
        Candidate separate = new Candidate(new Rectangle2D.Double(300, 300, 40, 40), 0.7);

        assertThat(YuNetFaceDetector.nms(List.of(overlapping, separate, best))).containsExactly(best, separate);
    }

    private static YuNetFaceDetector detector(String model) {
        RedactionProperties properties = new RedactionProperties("", null, List.of(), model, 0, null);
        return new YuNetFaceDetector(properties, new DefaultResourceLoader());
    }
}
