package com.anarapport.io;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Loads images from disk into BufferedImage instances.
 */
public final class ImageLoader {

    private ImageLoader() {
    }

    public static BufferedImage load(File file) throws IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            // ImageIO returns null (instead of throwing) when no reader accepts the file
            throw new IOException("Unsupported or unreadable image file: " + file.getName());
        }
        return image;
    }
}
