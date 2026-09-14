package com.anarapport.ui;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileView;

/**
 * Wires image thumbnails into a {@link JFileChooser}: a {@link FileView} that
 * shows a small preview icon per PNG/JPEG row instead of the generic system
 * icon, plus an accessory panel with an enlarged preview of the current
 * selection.
 */
public final class ImageFileChooserThumbnails {

    private ImageFileChooserThumbnails() {
    }

    public static void install(JFileChooser fileChooser) {
        fileChooser.setFileView(new ImageThumbnailFileView(fileChooser));

        ImagePreviewAccessory accessory = new ImagePreviewAccessory();
        fileChooser.setAccessory(accessory);
        fileChooser.addPropertyChangeListener(JFileChooser.SELECTED_FILE_CHANGED_PROPERTY, accessory);
    }
}
