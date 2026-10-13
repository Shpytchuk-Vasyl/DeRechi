package org.shpytchuk.ingest.support;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Random;

public final class Images {

    private Images() {
    }

    public static BufferedImage noise(int width, int height, long seed) {
        Random random = new Random(seed);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, random.nextInt(0x1000000));
            }
        }
        return image;
    }

    public static double variance(BufferedImage image, Rectangle box) {
        Rectangle inner = new Rectangle(box.x + box.width / 8, box.y + box.height / 8, box.width * 3 / 4, box.height * 3 / 4);
        double sum = 0;
        double squares = 0;
        int n = 0;
        for (int y = inner.y; y < inner.y + inner.height; y++) {
            for (int x = inner.x; x < inner.x + inner.width; x++) {
                int p = image.getRGB(x, y);
                double grey = (((p >> 16) & 0xFF) + ((p >> 8) & 0xFF) + (p & 0xFF)) / 3.0;
                sum += grey;
                squares += grey * grey;
                n++;
            }
        }
        double mean = sum / n;
        return squares / n - mean * mean;
    }
}
