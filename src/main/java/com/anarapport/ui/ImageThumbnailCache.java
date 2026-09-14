package com.anarapport.ui;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Renders and caches downscaled previews of image files for the JFileChooser
 * thumbnail FileView and its preview accessory.
 *
 * <p>Icons are generated off the EDT (on a small shared background pool) so
 * that browsing a folder full of images never blocks the file list. A
 * {@link ConcurrentHashMap} keeps one icon per file in memory so the same
 * thumbnail is never decoded twice, even across repeated visits to a folder
 * or repaints of the file list.
 */
final class ImageThumbnailCache {

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "image-thumbnail-loader");
        thread.setDaemon(true);
        return thread;
    });

    private static final ImageThumbnailCache LIST_CACHE = new ImageThumbnailCache(64);
    private static final ImageThumbnailCache PREVIEW_CACHE = new ImageThumbnailCache(200);

    private final int maxSize;
    private final Map<File, Icon> cache = new ConcurrentHashMap<>();
    private final Set<File> pending = ConcurrentHashMap.newKeySet();

    private ImageThumbnailCache(int maxSize) {
        this.maxSize = maxSize;
    }

    /** Shared cache sized for the file list's row icon (small). */
    static ImageThumbnailCache forList() {
        return LIST_CACHE;
    }

    /** Shared cache sized for the accessory's larger preview. */
    static ImageThumbnailCache forPreview() {
        return PREVIEW_CACHE;
    }

    /**
     * Returns the cached thumbnail for {@code file}, or {@code null} if it
     * hasn't been generated yet. When {@code null} is returned, generation is
     * kicked off in the background (unless already underway) and
     * {@code onReady} is invoked on the EDT once the icon is cached, so the
     * caller can re-request it and repaint.
     */
    Icon get(File file, Runnable onReady) {
        Icon cached = cache.get(file);
        if (cached != null) {
            return cached;
        }
        if (pending.add(file)) {
            EXECUTOR.submit(() -> {
                try {
                    Icon icon = renderThumbnail(file, maxSize);
                    if (icon != null) {
                        cache.put(file, icon);
                        if (onReady != null) {
                            SwingUtilities.invokeLater(onReady);
                        }
                    }
                } finally {
                    pending.remove(file);
                }
            });
        }
        return null;
    }

    private static Icon renderThumbnail(File file, int maxSize) {
        BufferedImage original;
        try {
            original = ImageIO.read(file);
        } catch (IOException e) {
            return null;
        }
        if (original == null) {
            return null;
        }

        int width = original.getWidth();
        int height = original.getHeight();
        double scale = Math.min(1.0, Math.min((double) maxSize / width, (double) maxSize / height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scaled.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g2.dispose();
        }
        return new ImageIcon(scaled);
    }
}
