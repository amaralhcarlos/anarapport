package com.anarapport.ui;

import com.anarapport.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.Locale;

/**
 * JFileChooser accessory that shows an enlarged preview of whichever file is
 * currently selected in the list, updated live via
 * {@link JFileChooser#SELECTED_FILE_CHANGED_PROPERTY}.
 *
 * <p>Like {@link ImageThumbnailFileView}, previews come from a shared,
 * memory-cached {@link ImageThumbnailCache} and are decoded off the EDT; a
 * generation still in flight when the user moves to a different file is
 * simply discarded on arrival instead of overwriting the newer selection.
 */
final class ImagePreviewAccessory extends JPanel implements PropertyChangeListener {

    private static final int PREVIEW_SIZE = 200;

    private final ImageThumbnailCache cache = ImageThumbnailCache.forPreview();
    private final JLabel previewLabel = new JLabel();

    private volatile File currentFile;

    ImagePreviewAccessory() {
        super(new BorderLayout());
        setPreferredSize(new Dimension(PREVIEW_SIZE + 24, PREVIEW_SIZE + 24));
        setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 8));
        previewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        previewLabel.setVerticalAlignment(SwingConstants.CENTER);
        add(previewLabel, BorderLayout.CENTER);
        showNoPreview();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (!JFileChooser.SELECTED_FILE_CHANGED_PROPERTY.equals(evt.getPropertyName())) {
            return;
        }
        updatePreview((File) evt.getNewValue());
    }

    private void updatePreview(File file) {
        currentFile = file;
        if (file == null || file.isDirectory() || !isImageFile(file)) {
            showNoPreview();
            return;
        }

        Icon icon = cache.get(file, () -> {
            if (file.equals(currentFile)) {
                updatePreview(file);
            }
        });
        if (icon == null) {
            showNoPreview();
        } else {
            previewLabel.setIcon(icon);
            previewLabel.setText(null);
        }
    }

    private void showNoPreview() {
        previewLabel.setIcon(null);
        previewLabel.setText(Messages.get("fileChooser.preview.none"));
    }

    private static boolean isImageFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }
}
