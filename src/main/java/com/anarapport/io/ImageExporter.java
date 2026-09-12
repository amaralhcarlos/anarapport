package com.anarapport.io;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Saves BufferedImage instances to disk in common raster formats.
 */
public final class ImageExporter {

    private ImageExporter() {
    }

    public static void save(BufferedImage image, File file, String formatName) throws IOException {
        BufferedImage exportable = isJpeg(formatName) ? withoutAlpha(image) : image;

        if (!ImageIO.write(exportable, formatName, file)) {
            throw new IOException("No writer available for format: " + formatName);
        }
    }

    private static boolean isJpeg(String formatName) {
        return "jpg".equalsIgnoreCase(formatName) || "jpeg".equalsIgnoreCase(formatName);
    }

    // JPEG has no alpha channel; flatten onto a white background before writing
    private static BufferedImage withoutAlpha(BufferedImage image) {
        BufferedImage flattened = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = flattened.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        return flattened;
    }
}
