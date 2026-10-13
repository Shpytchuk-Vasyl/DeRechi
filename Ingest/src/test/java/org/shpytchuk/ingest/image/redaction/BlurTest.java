package org.shpytchuk.ingest.image.redaction;

import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.support.Images;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class BlurTest {

    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;

    @Test
    void blursTheBoxAndItsMarginOnly() {
        BufferedImage image = Images.noise(WIDTH, HEIGHT, 1);
        BufferedImage original = copy(image);
        Rectangle box = new Rectangle(100, 100, 100, 50);

        Blur.region(image, box);

        Rectangle blurred = Blur.expand(box, WIDTH, HEIGHT);
        assertThat(blurred).isEqualTo(new Rectangle(80, 90, 140, 70));
        int changedInside = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                boolean same = image.getRGB(x, y) == original.getRGB(x, y);
                if (!blurred.contains(x, y)) {
                    assertThat(same).as("pixel %d,%d outside the box", x, y).isTrue();
                } else if (!same) {
                    changedInside++;
                }
            }
        }
        assertThat(changedInside).isGreaterThan(blurred.width * blurred.height * 9 / 10);
    }

    @Test
    void blurredAreaHasLowVariance() {
        BufferedImage image = Images.noise(WIDTH, HEIGHT, 2);
        Rectangle box = new Rectangle(50, 50, 300, 200);
        double before = Images.variance(image, box);

        Blur.region(image, box);

        assertThat(Images.variance(image, box)).isLessThan(before / 50);
    }

    @Test
    void textBecomesUnreadable() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                image.setRGB(x, y, x % 4 < 2 ? 0x000000 : 0xFFFFFF);
            }
        }
        Rectangle line = new Rectangle(40, 120, 320, 40);

        Blur.region(image, line);

        assertThat(Images.variance(image, line)).isLessThan(100);
    }

    @Test
    void keepsTheColour() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(3);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                image.setRGB(x, y, random.nextInt(10) == 0 ? 0x101010 : 0x2050D0);
            }
        }

        BufferedImage blurred = Blur.whole(image);

        int centre = blurred.getRGB(WIDTH / 2, HEIGHT / 2);
        int red = (centre >> 16) & 0xFF;
        int blue = centre & 0xFF;
        assertThat(blue).isGreaterThan(150);
        assertThat(red).isLessThan(60);
    }

    @Test
    void wholeBlurChangesTheWholeImage() {
        BufferedImage image = Images.noise(WIDTH, HEIGHT, 4);

        BufferedImage blurred = Blur.whole(image);

        assertThat(blurred.getWidth()).isEqualTo(WIDTH);
        assertThat(blurred.getHeight()).isEqualTo(HEIGHT);
        int changed = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (blurred.getRGB(x, y) != image.getRGB(x, y)) {
                    changed++;
                }
            }
        }
        assertThat(changed).isGreaterThan(WIDTH * HEIGHT * 95 / 100);
        Rectangle all = new Rectangle(0, 0, WIDTH, HEIGHT);
        assertThat(Images.variance(blurred, all)).isLessThan(Images.variance(image, all) / 50);
    }

    @Test
    void allButKeepsTheBoxesWithTheirMargin() {
        BufferedImage image = Images.noise(WIDTH, HEIGHT, 7);
        Rectangle face = new Rectangle(40, 60, 80, 100);

        BufferedImage blurred = Blur.allBut(image, List.of(face));

        Rectangle kept = Blur.expand(face, WIDTH, HEIGHT);
        assertThat(sameAs(blurred.getSubimage(kept.x, kept.y, kept.width, kept.height),
                image.getSubimage(kept.x, kept.y, kept.width, kept.height))).isTrue();
        Rectangle rest = new Rectangle(200, 0, 200, HEIGHT);
        assertThat(Images.variance(blurred, rest)).isLessThan(Images.variance(image, rest) / 50);
    }

    @Test
    void regionUpToRightEdgeDoesNotSpillToTheRight() {
        BufferedImage image = Images.noise(WIDTH, HEIGHT, 8);
        BufferedImage original = copy(image);

        Blur.regionUpToRightEdge(image, new Rectangle(100, 100, 150, 40));

        assertThat(image.getRGB(260, 120)).isEqualTo(original.getRGB(260, 120));
        assertThat(Images.variance(image, new Rectangle(100, 100, 150, 40)))
                .isLessThan(Images.variance(original, new Rectangle(100, 100, 150, 40)) / 50);
    }

    @Test
    void boxOutsideTheImageIsIgnoredAndEdgesAreClamped() {
        BufferedImage image = Images.noise(100, 100, 5);
        BufferedImage original = copy(image);

        Blur.region(image, new Rectangle(500, 500, 10, 10));
        assertThat(sameAs(image, original)).isTrue();

        Blur.region(image, new Rectangle(-20, -20, 40, 40));
        assertThat(Blur.expand(new Rectangle(-20, -20, 40, 40), 100, 100)).isEqualTo(new Rectangle(0, 0, 28, 28));
        assertThat(image.getRGB(50, 50)).isEqualTo(original.getRGB(50, 50));
        assertThat(image.getRGB(5, 5)).isNotEqualTo(original.getRGB(5, 5));
    }

    @Test
    void tinyBoxesDoNotFail() {
        BufferedImage image = Images.noise(50, 50, 6);

        Blur.region(image, new Rectangle(10, 10, 1, 1));
        Blur.region(image, new Rectangle(20, 20, 3, 2));
        BufferedImage whole = Blur.whole(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));

        assertThat(whole.getWidth()).isEqualTo(1);
    }

    private static BufferedImage copy(BufferedImage image) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        copy.setData(image.getData());
        return copy;
    }

    private static boolean sameAs(BufferedImage a, BufferedImage b) {
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }
}
