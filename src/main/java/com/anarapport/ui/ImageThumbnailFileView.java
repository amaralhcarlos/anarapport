package com.anarapport.ui;

import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileView;
import java.io.File;
import java.util.Locale;

/**
 * FileView that swaps the OS's generic file icon for a small decoded preview
 * of each PNG/JPEG file listed in a {@link JFileChooser}. Non-image files and
 * directories fall back to {@code null}, which tells the chooser's UI to use
 * its normal look-and-feel icon.
 *
 * <p>Thumbnails come from a shared, memory-cached {@link ImageThumbnailCache}
 * generated off the EDT; while a thumbnail is still being decoded this
 * returns {@code null} (default icon) and repaints the chooser once it's
 * ready, so browsing large folders never blocks on decoding.
 */
final class ImageThumbnailFileView extends FileView {

    private final JFileChooser fileChooser;
    private final ImageThumbnailCache cache = ImageThumbnailCache.forList();

    ImageThumbnailFileView(JFileChooser fileChooser) {
        this.fileChooser = fileChooser;
    }

    @Override
    public Icon getIcon(File file) {
        if (!isThumbnailable(file)) {
            return null;
        }
        return cache.get(file, fileChooser::repaint);
    }

    private static boolean isThumbnailable(File file) {
        if (file.isDirectory()) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }
}
