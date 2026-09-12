package com.anarapport.ui;

import com.anarapport.model.ExifInfo;
import com.anarapport.model.ImageMetadata;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Read-only dialog listing an image file's technical metadata, aimed at someone
 * who will rework the file elsewhere (e.g. Photoshop): dimensions, resolution,
 * color mode/bit depth, ICC profile, file info and EXIF, when available.
 */
public final class ImageInfoDialog {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter LAST_MODIFIED_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", PT_BR);
    private static final String NOT_AVAILABLE = "Não informado";

    private ImageInfoDialog() {
    }

    public static void show(JFrame owner, ImageMetadata metadata) {
        JDialog dialog = new JDialog(owner, "Informações da imagem", false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setContentPane(buildContent(metadata));
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        dialog.toFront();
    }

    private static JPanel buildContent(ImageMetadata metadata) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        int row = 0;
        row = addSectionTitle(panel, "Arquivo", row);
        row = addRow(panel, "Nome:", metadata.fileName(), row);
        row = addRow(panel, "Caminho completo:", metadata.filePath(), row);
        row = addRow(panel, "Formato:", metadata.fileFormat(), row);
        row = addRow(panel, "Tamanho em disco:", formatFileSize(metadata.fileSizeBytes()), row);
        row = addRow(panel, "Última modificação:", formatLastModified(metadata), row);

        row = addSectionTitle(panel, "Dimensões", row);
        row = addRow(panel, "Pixels:", metadata.pixelWidth() + " x " + metadata.pixelHeight() + " px", row);
        row = addRow(panel, "Resolução:", formatDpi(metadata), row);
        row = addRow(panel, "Tamanho físico estimado:", formatPhysicalSize(metadata), row);

        row = addSectionTitle(panel, "Cor", row);
        row = addRow(panel, "Modo de cor:", orDefault(metadata.colorMode()), row);
        row = addRow(panel, "Profundidade de bit:", formatBitDepth(metadata.bitsPerChannel()), row);
        row = addRow(panel, "Perfil ICC embutido:", orDefault(metadata.iccProfileName(), "Nenhum"), row);

        row = addSectionTitle(panel, "EXIF", row);
        ExifInfo exif = metadata.exif();
        if (exif.isEmpty()) {
            addRow(panel, "Metadados EXIF:", "Não encontrados neste arquivo", row);
        } else {
            row = addRow(panel, "Câmera/scanner:", formatCameraSource(exif), row);
            row = addRow(panel, "Data de captura:", orDefault(exif.captureDate()), row);
            addRow(panel, "Orientação:", orDefault(exif.orientation()), row);
        }

        return panel;
    }

    private static int addSectionTitle(JPanel panel, String title, int row) {
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 2;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(row == 0 ? 0 : 14, 0, 4, 0);
        panel.add(titleLabel, constraints);
        return row + 1;
    }

    private static int addRow(JPanel panel, String label, String value, int row) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.NORTHWEST;
        labelConstraints.insets = new Insets(2, 0, 2, 16);
        panel.add(new JLabel(label), labelConstraints);

        GridBagConstraints valueConstraints = new GridBagConstraints();
        valueConstraints.gridx = 1;
        valueConstraints.gridy = row;
        valueConstraints.anchor = GridBagConstraints.NORTHWEST;
        valueConstraints.fill = GridBagConstraints.HORIZONTAL;
        valueConstraints.weightx = 1;
        valueConstraints.insets = new Insets(2, 0, 2, 0);
        panel.add(new JLabel(value), valueConstraints);

        return row + 1;
    }

    private static String orDefault(String value) {
        return orDefault(value, NOT_AVAILABLE);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kilobytes = bytes / 1024.0;
        if (kilobytes < 1024) {
            return String.format(PT_BR, "%.1f KB", kilobytes);
        }
        return String.format(PT_BR, "%.2f MB", kilobytes / 1024.0);
    }

    private static String formatLastModified(ImageMetadata metadata) {
        return LAST_MODIFIED_FORMAT.withZone(ZoneId.systemDefault()).format(metadata.lastModified());
    }

    private static String formatDpi(ImageMetadata metadata) {
        if (!metadata.hasKnownDpi()) {
            return NOT_AVAILABLE + " (arquivo não especifica DPI)";
        }
        return String.format(PT_BR, "%.0f x %.0f DPI", metadata.horizontalDpi(), metadata.verticalDpi());
    }

    private static String formatPhysicalSize(ImageMetadata metadata) {
        String suffix = metadata.hasKnownDpi()
                ? ""
                : " (estimado, assumindo " + (int) metadata.assumedDpiForEstimate() + " DPI)";
        return String.format(PT_BR, "%.1f x %.1f cm  /  %.2f x %.2f pol%s",
                metadata.widthCm(), metadata.heightCm(), metadata.widthInches(), metadata.heightInches(), suffix);
    }

    private static String formatBitDepth(Integer bitsPerChannel) {
        return bitsPerChannel != null ? bitsPerChannel + " bits" : NOT_AVAILABLE;
    }

    private static String formatCameraSource(ExifInfo exif) {
        String make = exif.cameraMake();
        String model = exif.cameraModel();
        if (make == null && model == null) {
            return NOT_AVAILABLE;
        }
        if (make == null) {
            return model;
        }
        if (model == null) {
            return make;
        }
        // Many cameras repeat the make inside the model string (e.g. "Canon" + "Canon EOS 90D")
        return model.startsWith(make) ? model : make + " " + model;
    }
}
