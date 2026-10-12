package org.shpytchuk.ingest.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.shpytchuk.ingest.config.RedactionProperties;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "TESSDATA_PREFIX", matches = ".+")
class TesseractOcrEngineIT {

    private final TesseractOcrEngine engine = new TesseractOcrEngine(new RedactionProperties(
            System.getenv("TESSDATA_PREFIX"), System.getenv().getOrDefault("REDACTION_OCR_LANGUAGES", "eng"),
            List.of(), null, 0, null));

    @Test
    void readsAPhoneNumberAndItsLine() {
        BufferedImage image = poster(0);

        List<OcrLine> lines = engine.lines(image);

        OcrLine phone = lines.stream().filter(l -> digits(l).contains("0671234567")).findFirst()
                .orElseThrow(() -> new AssertionError("no line with the number among " + lines.size()));
        assertThat(phone.box().y).isBetween(150, 260);
        assertThat(phone.confidence()).isBetween(0.0, 1.0);
    }

    @Test
    void readsASkewedPosterAndMapsTheBoxBack() {
        List<OcrLine> lines = engine.lines(poster(Math.toRadians(5)));

        assertThat(lines).anySatisfy(l -> assertThat(digits(l)).contains("0671234567"));
        assertThat(lines).allSatisfy(l -> assertThat(new Rectangle(0, 0, 900, 400).contains(l.box())).isTrue());
    }

    private static String digits(OcrLine line) {
        return line.text().replaceAll("[^0-9]", "");
    }

    private static BufferedImage poster(double radians) {
        BufferedImage image = new BufferedImage(900, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 900, 400);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setTransform(AffineTransform.getRotateInstance(radians, 450, 200));
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 48));
        g.drawString("LOST WALLET", 60, 110);
        g.drawString("CALL 067 123 45 67", 60, 220);
        g.dispose();
        return image;
    }
}
