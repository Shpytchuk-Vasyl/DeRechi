package org.shpytchuk.telegramingest.image;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;


final class Blur {

    private static final double MARGIN = 0.2;
    private static final int MIN_CELL = 12;
    private static final int REGION_CELLS = 3;
    private static final int WHOLE_CELLS = 32;

    private Blur() {
    }

    static Rectangle expand(Rectangle box, int width, int height) {
        Rectangle grown = new Rectangle(box);
        grown.grow((int) Math.ceil(box.width * MARGIN), (int) Math.ceil(box.height * MARGIN));
        return grown.intersection(new Rectangle(width, height));
    }

    static void region(BufferedImage image, Rectangle box) {
        blurArea(image, expand(box, image.getWidth(), image.getHeight()));
    }

    static void regionUpToRightEdge(BufferedImage image, Rectangle box) {
        Rectangle area = expand(box, image.getWidth(), image.getHeight());
        area.width = Math.min(area.width, box.x + box.width - area.x);
        blurArea(image, area);
    }

    static BufferedImage whole(BufferedImage image) {
        double cell = Math.max(MIN_CELL, Math.max(image.getWidth(), image.getHeight()) / (double) WHOLE_CELLS);
        return blurred(image, cell);
    }

    static BufferedImage allBut(BufferedImage image, List<Rectangle> kept) {
        BufferedImage blurred = whole(image);
        Graphics2D g = blurred.createGraphics();
        for (Rectangle box : kept) {
            Rectangle area = expand(box, image.getWidth(), image.getHeight());
            if (!area.isEmpty()) {
                g.drawImage(image.getSubimage(area.x, area.y, area.width, area.height), area.x, area.y, null);
            }
        }
        g.dispose();
        return blurred;
    }

    private static void blurArea(BufferedImage image, Rectangle area) {
        if (area.isEmpty()) {
            return;
        }
        double cell = Math.max(MIN_CELL, Math.min(area.width, area.height) / (double) REGION_CELLS);
        BufferedImage blurred = blurred(image.getSubimage(area.x, area.y, area.width, area.height), cell);
        Graphics2D g = image.createGraphics();
        g.drawImage(blurred, area.x, area.y, null);
        g.dispose();
    }

    private static BufferedImage blurred(BufferedImage source, double cell) {
        int width = source.getWidth();
        int height = source.getHeight();
        Image cells = source.getScaledInstance(cells(width, cell), cells(height, cell), Image.SCALE_AREA_AVERAGING);
        BufferedImage blurred = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = blurred.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(cells, 0, 0, width, height, null);
        g.dispose();
        return blurred;
    }

    private static int cells(int side, double cell) {
        return Math.max(1, (int) Math.round(side / cell));
    }
}
