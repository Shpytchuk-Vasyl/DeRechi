package org.shpytchuk.ingest.image.face;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.config.redaction.RedactionProperties;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

@Component
@Slf4j
public class YuNetFaceDetector implements FaceDetector, AutoCloseable {

    private static final int INPUT_SIZE = 640;
    private static final String INPUT = "input";
    private static final int[] STRIDES = {8, 16, 32};
    private static final float NMS_THRESHOLD = 0.3f;
    private static final int[] BGR_BANDS = {2, 1, 0};

    private final double scoreThreshold;
    private final @Nullable OrtSession session;

    public YuNetFaceDetector(RedactionProperties properties, ResourceLoader resourceLoader) {
        this.scoreThreshold = properties.faceScore();
        this.session = load(properties.faceModel(), resourceLoader);
    }

    @Override
    public List<Rectangle> faces(BufferedImage image) {
        if (session == null) {
            throw new IllegalStateException("face model is not loaded");
        }
        double scale = Math.min(INPUT_SIZE / (double) image.getWidth(), INPUT_SIZE / (double) image.getHeight());
        try (OnnxTensor tensor = OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), input(image, scale),
                     new long[]{1, 3, INPUT_SIZE, INPUT_SIZE});
             OrtSession.Result result = session.run(Map.of(INPUT, tensor))) {
            List<Candidate> candidates = new ArrayList<>();
            for (int stride : STRIDES) {
                decode(result, stride, candidates);
            }
            AffineTransform toImage = AffineTransform.getScaleInstance(1 / scale, 1 / scale);
            Rectangle bounds = new Rectangle(image.getWidth(), image.getHeight());
            return nms(candidates).stream()
                    .map(c -> toImage.createTransformedShape(c.box()).getBounds().intersection(bounds))
                    .filter(box -> !box.isEmpty())
                    .toList();
        } catch (OrtException e) {
            throw new IllegalStateException("face detection failed", e);
        }
    }

    private static FloatBuffer input(BufferedImage image, double scale) {
        BufferedImage square = new BufferedImage(INPUT_SIZE, INPUT_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = square.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, (int) Math.round(image.getWidth() * scale), (int) Math.round(image.getHeight() * scale), null);
        g.dispose();

        Raster raster = square.getRaster();
        FloatBuffer chw = FloatBuffer.allocate(3 * INPUT_SIZE * INPUT_SIZE);
        for (int band : BGR_BANDS) {
            chw.put(raster.getSamples(0, 0, INPUT_SIZE, INPUT_SIZE, band, (float[]) null));
        }
        return chw.rewind();
    }

    private void decode(OrtSession.Result result, int stride, List<Candidate> out) {
        FloatBuffer cls = output(result, "cls_" + stride);
        FloatBuffer obj = output(result, "obj_" + stride);
        FloatBuffer bbox = output(result, "bbox_" + stride);
        int cols = INPUT_SIZE / stride;
        for (int i = 0; i < cls.limit(); i++) {
            double score = Math.sqrt(Math.clamp(cls.get(i), 0f, 1f) * Math.clamp(obj.get(i), 0f, 1f));
            if (score < scoreThreshold) {
                continue;
            }
            double cx = (i % cols + bbox.get(4 * i)) * stride;
            double cy = (i / cols + bbox.get(4 * i + 1)) * stride;
            double w = Math.exp(bbox.get(4 * i + 2)) * stride;
            double h = Math.exp(bbox.get(4 * i + 3)) * stride;
            out.add(new Candidate(new Rectangle2D.Double(cx - w / 2, cy - h / 2, w, h), score));
        }
    }

    private static FloatBuffer output(OrtSession.Result result, String name) {
        return ((OnnxTensor) result.get(name).orElseThrow(() -> new IllegalStateException("face model has no output " + name)))
                .getFloatBuffer();
    }

    static List<Candidate> nms(List<Candidate> candidates) {
        List<Candidate> sorted = candidates.stream().sorted(Comparator.comparingDouble(Candidate::score).reversed()).toList();
        List<Candidate> kept = new ArrayList<>();
        for (Candidate candidate : sorted) {
            if (kept.stream().noneMatch(k -> iou(k.box(), candidate.box()) > NMS_THRESHOLD)) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    private static double iou(Rectangle2D a, Rectangle2D b) {
        Rectangle2D overlap = a.createIntersection(b);
        double intersection = overlap.isEmpty() ? 0 : overlap.getWidth() * overlap.getHeight();
        return intersection / (a.getWidth() * a.getHeight() + b.getWidth() * b.getHeight() - intersection);
    }

    private static @Nullable OrtSession load(String location, ResourceLoader resourceLoader) {
        try {
            byte[] model = resourceLoader.getResource(location).getContentAsByteArray();
            OrtSession session = OrtEnvironment.getEnvironment().createSession(model);
            log.info("Face model {} loaded", location);
            return session;
        } catch (IOException | OrtException | LinkageError e) {
            log.error("Face model {} is not available, document photos will be blurred entirely: {}",
                    location, getMostSpecificCause(e).toString());
            return null;
        }
    }

    @Override
    public void close() throws OrtException {
        if (session != null) {
            session.close();
        }
    }

    record Candidate(Rectangle2D box, double score) {
    }
}
