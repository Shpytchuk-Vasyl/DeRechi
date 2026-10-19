package org.shpytchuk.ingest.image;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

public final class Rotations {

    private Rotations() {
    }

    public static BufferedImage quarterTurns(BufferedImage image, int turns) {
        int clockwise = Math.floorMod(turns, 4);
        if (clockwise == 0) {
            return image;
        }
        boolean sideways = clockwise % 2 == 1;
        int width = sideways ? image.getHeight() : image.getWidth();
        int height = sideways ? image.getWidth() : image.getHeight();
        int type = image.getType() == BufferedImage.TYPE_CUSTOM ? BufferedImage.TYPE_INT_RGB : image.getType();
        BufferedImage turned = new BufferedImage(width, height, type);
        AffineTransform transform = new AffineTransform();
        transform.translate(width / 2.0, height / 2.0);
        transform.quadrantRotate(clockwise);
        transform.translate(-image.getWidth() / 2.0, -image.getHeight() / 2.0);
        Graphics2D g = turned.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(image, transform, null);
        g.dispose();
        return turned;
    }
}
