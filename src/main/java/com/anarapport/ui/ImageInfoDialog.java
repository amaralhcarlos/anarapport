package com.anarapport.ui;

import com.anarapport.analysis.AnalysisOptions;
import com.anarapport.analysis.AnalysisResult;
import com.anarapport.analysis.DominantColor;
import com.anarapport.analysis.EdgeContinuityResult;
import com.anarapport.analysis.GamutCheckResult;
import com.anarapport.analysis.ImageAnalyzer;
import com.anarapport.analysis.ResolutionCheck;
import com.anarapport.i18n.Messages;
import com.anarapport.io.ImageMetadataReader;
import com.anarapport.model.ColorMode;
import com.anarapport.model.ExifInfo;
import com.anarapport.model.ImageMetadata;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Read-only dialog listing an image file's technical metadata, aimed at someone
 * who will rework the file elsewhere (e.g. Photoshop): dimensions, resolution,
 * color mode/bit depth, ICC profile, file info and EXIF, when available. Also
 * offers an on-demand "advanced analysis" section (pixel-level metrics), run
 * via {@link ImageAnalyzer} in a background SwingWorker with a progress bar.
 *
 * <p>Built fresh every time {@link #show} is called, so it always reflects
 * whatever language is active at that moment -- no special handling is needed
 * for a runtime language switch here.
 */
public final class ImageInfoDialog {

    private ImageInfoDialog() {
    }

    public static void show(JFrame owner, ImageMetadata metadata, BufferedImage image) {
        JDialog dialog = new JDialog(owner, Messages.get("dialog.info.title"), false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.add(buildContent(metadata));
        root.add(buildAdvancedAnalysisSection(dialog, metadata, image));

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        dialog.toFront();
    }

    private static JPanel buildContent(ImageMetadata metadata) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        int row = 0;
        row = addSectionTitle(panel, Messages.get("dialog.info.section.file"), row);
        row = addRow(panel, Messages.get("dialog.info.file.name"), metadata.fileName(), row);
        row = addRow(panel, Messages.get("dialog.info.file.path"), metadata.filePath(), row);
        row = addRow(panel, Messages.get("dialog.info.file.format"), metadata.fileFormat(), row);
        row = addRow(panel, Messages.get("dialog.info.file.size"), formatFileSize(metadata.fileSizeBytes()), row);
        row = addRow(panel, Messages.get("dialog.info.file.modified"), formatLastModified(metadata), row);

        row = addSectionTitle(panel, Messages.get("dialog.info.section.dimensions"), row);
        row = addRow(panel, Messages.get("dialog.info.dimensions.pixels"),
                Messages.get("dialog.info.dimensions.pixelsValue", metadata.pixelWidth(), metadata.pixelHeight()), row);
        row = addRow(panel, Messages.get("dialog.info.dimensions.resolution"), formatDpi(metadata), row);
        row = addRow(panel, Messages.get("dialog.info.dimensions.physicalSize"), formatPhysicalSize(metadata), row);

        row = addSectionTitle(panel, Messages.get("dialog.info.section.color"), row);
        row = addRow(panel, Messages.get("dialog.info.color.mode"), formatColorMode(metadata), row);
        row = addRow(panel, Messages.get("dialog.info.color.bitDepth"), formatBitDepth(metadata.bitsPerChannel()), row);
        row = addRow(panel, Messages.get("dialog.info.color.icc"),
                orDefault(metadata.iccProfileName(), Messages.get("common.none")), row);

        row = addSectionTitle(panel, Messages.get("dialog.info.section.exif"), row);
        ExifInfo exif = metadata.exif();
        if (exif.isEmpty()) {
            addRow(panel, Messages.get("dialog.info.exif.camera"), Messages.get("dialog.info.exif.none"), row);
        } else {
            row = addRow(panel, Messages.get("dialog.info.exif.camera"), formatCameraSource(exif), row);
            row = addRow(panel, Messages.get("dialog.info.exif.captureDate"), orDefault(exif.captureDate()), row);
            addRow(panel, Messages.get("dialog.info.exif.orientation"), formatOrientation(exif.orientationCode()), row);
        }

        return panel;
    }

    /**
     * Builds the "Análise avançada"/"Advanced analysis" section: print-size/
     * DPI-threshold inputs, an "Analisar imagem"/"Analyze image" button, a
     * progress bar, and a results area that starts empty and is replaced after
     * each run. Nothing here runs until the button is clicked, since the
     * analysis is pixel-level and can take a while.
     */
    private static JPanel buildAdvancedAnalysisSection(JDialog dialog, ImageMetadata metadata, BufferedImage image) {
        JPanel section = new JPanel(new GridBagLayout());
        section.setBorder(BorderFactory.createEmptyBorder(0, 16, 12, 16));

        int row = 0;
        row = addSectionTitle(section, Messages.get("dialog.analysis.section"), row);

        GridBagConstraints fullWidth = new GridBagConstraints();
        fullWidth.gridx = 0;
        fullWidth.gridy = row++;
        fullWidth.gridwidth = 2;
        fullWidth.anchor = GridBagConstraints.NORTHWEST;
        fullWidth.insets = new Insets(0, 0, 8, 0);
        JLabel explanation = new JLabel("<html>" + Messages.get("dialog.analysis.explanation") + "</html>");
        section.add(explanation, fullWidth);

        JCheckBox checkResolution = new JCheckBox(Messages.get("dialog.analysis.checkResolution"));
        SpinnerNumberModel widthModel = new SpinnerNumberModel(10.0, 0.1, 1000.0, 0.5);
        JSpinner widthSpinner = new JSpinner(widthModel);
        SpinnerNumberModel heightModel = new SpinnerNumberModel(10.0, 0.1, 1000.0, 0.5);
        JSpinner heightSpinner = new JSpinner(heightModel);
        // Index-based (0 = cm, 1 = inches) rather than comparing the localized
        // label text, so the unit check works regardless of the active language.
        JComboBox<String> unitCombo = new JComboBox<>(new String[] {
                Messages.get("dialog.analysis.unit.cm"), Messages.get("dialog.analysis.unit.inches")});
        widthSpinner.setEnabled(false);
        heightSpinner.setEnabled(false);
        unitCombo.setEnabled(false);
        checkResolution.addActionListener(event -> {
            boolean enabled = checkResolution.isSelected();
            widthSpinner.setEnabled(enabled);
            heightSpinner.setEnabled(enabled);
            unitCombo.setEnabled(enabled);
        });

        JPanel printSizeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        printSizeRow.add(checkResolution);
        printSizeRow.add(widthSpinner);
        printSizeRow.add(new JLabel("x"));
        printSizeRow.add(heightSpinner);
        printSizeRow.add(unitCombo);
        GridBagConstraints printSizeConstraints = new GridBagConstraints();
        printSizeConstraints.gridx = 0;
        printSizeConstraints.gridy = row++;
        printSizeConstraints.gridwidth = 2;
        printSizeConstraints.anchor = GridBagConstraints.NORTHWEST;
        section.add(printSizeRow, printSizeConstraints);

        JLabel minDpiLabel = new JLabel(Messages.get("dialog.analysis.minDpi"));
        SpinnerNumberModel minDpiModel = new SpinnerNumberModel(
                AnalysisOptions.DEFAULT_MIN_RECOMMENDED_DPI, 72, 1200, 1);
        JSpinner minDpiSpinner = new JSpinner(minDpiModel);
        JPanel minDpiRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        minDpiRow.add(minDpiLabel);
        minDpiRow.add(minDpiSpinner);
        GridBagConstraints minDpiConstraints = new GridBagConstraints();
        minDpiConstraints.gridx = 0;
        minDpiConstraints.gridy = row++;
        minDpiConstraints.gridwidth = 2;
        minDpiConstraints.anchor = GridBagConstraints.NORTHWEST;
        minDpiConstraints.insets = new Insets(0, 0, 8, 0);
        section.add(minDpiRow, minDpiConstraints);

        JButton analyzeButton = new JButton(Messages.get("dialog.analysis.button"));
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actionRow.add(analyzeButton);
        actionRow.add(progressBar);
        GridBagConstraints actionConstraints = new GridBagConstraints();
        actionConstraints.gridx = 0;
        actionConstraints.gridy = row++;
        actionConstraints.gridwidth = 2;
        actionConstraints.anchor = GridBagConstraints.NORTHWEST;
        actionConstraints.insets = new Insets(0, 0, 8, 0);
        section.add(actionRow, actionConstraints);

        JPanel resultsPanel = new JPanel();
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        resultsPanel.add(new JLabel(Messages.get("dialog.analysis.noneYet")));
        GridBagConstraints resultsConstraints = new GridBagConstraints();
        resultsConstraints.gridx = 0;
        resultsConstraints.gridy = row;
        resultsConstraints.gridwidth = 2;
        resultsConstraints.anchor = GridBagConstraints.NORTHWEST;
        resultsConstraints.fill = GridBagConstraints.HORIZONTAL;
        section.add(resultsPanel, resultsConstraints);

        analyzeButton.addActionListener(event -> runAnalysis(dialog, metadata, image, checkResolution,
                widthSpinner, heightSpinner, unitCombo, minDpiSpinner, analyzeButton, progressBar, resultsPanel));

        return section;
    }

    private static void runAnalysis(JDialog dialog, ImageMetadata metadata, BufferedImage image,
                                     JCheckBox checkResolution, JSpinner widthSpinner, JSpinner heightSpinner,
                                     JComboBox<String> unitCombo, JSpinner minDpiSpinner, JButton analyzeButton,
                                     JProgressBar progressBar, JPanel resultsPanel) {
        Double printWidthCm = null;
        Double printHeightCm = null;
        if (checkResolution.isSelected()) {
            double width = (Double) widthSpinner.getValue();
            double height = (Double) heightSpinner.getValue();
            boolean isInches = unitCombo.getSelectedIndex() == 1;
            printWidthCm = isInches ? width * 2.54 : width;
            printHeightCm = isInches ? height * 2.54 : height;
        }
        AnalysisOptions options = new AnalysisOptions(printWidthCm, printHeightCm, (Integer) minDpiSpinner.getValue());
        boolean isJpeg = "JPEG".equals(metadata.fileFormat());
        File imageFile = new File(metadata.filePath());

        analyzeButton.setEnabled(false);
        progressBar.setValue(0);
        progressBar.setVisible(true);

        SwingWorker<AnalysisResult, Void> worker = new SwingWorker<>() {
            @Override
            protected AnalysisResult doInBackground() throws Exception {
                int[] quantTable = isJpeg ? ImageMetadataReader.readJpegLuminanceQuantTable(imageFile) : null;
                return new ImageAnalyzer().analyze(image, quantTable, options, this::setProgress);
            }

            @Override
            protected void done() {
                analyzeButton.setEnabled(true);
                progressBar.setVisible(false);
                try {
                    AnalysisResult result = get();
                    resultsPanel.removeAll();
                    resultsPanel.add(buildResultsPanel(result));
                    resultsPanel.revalidate();
                    resultsPanel.repaint();
                    dialog.pack();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    JOptionPane.showMessageDialog(dialog,
                            Messages.get("dialog.analysis.error.message", e.getCause().getMessage()),
                            Messages.get("dialog.analysis.error.title"), JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.addPropertyChangeListener(event -> {
            if ("progress".equals(event.getPropertyName())) {
                progressBar.setValue((Integer) event.getNewValue());
            }
        });
        worker.execute();
    }

    private static JPanel buildResultsPanel(AnalysisResult result) {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

        JPanel textGrid = new JPanel(new GridBagLayout());
        int row = 0;
        row = addRow(textGrid, Messages.get("dialog.analysis.result.uniqueColors"),
                String.valueOf(result.uniqueColorCount()), row);
        row = addRow(textGrid, Messages.get("dialog.analysis.result.alpha"), formatAlpha(result), row);
        row = addRow(textGrid, Messages.get("dialog.analysis.result.gamut"), formatGamut(result.gamutCheck()), row);
        if (result.resolutionCheck() != null) {
            row = addRow(textGrid, Messages.get("dialog.analysis.result.resolution"),
                    formatResolutionCheck(result.resolutionCheck()), row);
        }
        if (result.estimatedJpegQuality() != null) {
            row = addRow(textGrid, Messages.get("dialog.analysis.result.jpegQuality"),
                    Messages.get("dialog.analysis.result.jpegQualityValue", result.estimatedJpegQuality()), row);
        }
        addRow(textGrid, Messages.get("dialog.analysis.result.continuity"), formatEdgeContinuity(result.edgeContinuity()), row);
        container.add(textGrid);

        JLabel paletteLabel = new JLabel(Messages.get("dialog.analysis.result.dominantColors"));
        paletteLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 4, 0));
        container.add(paletteLabel);
        container.add(buildDominantColorsRow(result.dominantColors()));

        return container;
    }

    private static JPanel buildDominantColorsRow(List<DominantColor> colors) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        for (DominantColor color : colors) {
            JPanel swatch = new JPanel();
            swatch.setPreferredSize(new Dimension(28, 28));
            swatch.setBackground(new Color(color.rgb()));
            swatch.setBorder(BorderFactory.createLineBorder(Color.GRAY));

            JLabel label = new JLabel("<html>" + color.toHex() + "<br>rgb(" + color.red() + "," + color.green()
                    + "," + color.blue() + ")<br>" + String.format(Messages.getLocale(), "%.1f%%", color.percentOfImage())
                    + "</html>");
            label.setFont(label.getFont().deriveFont(11f));

            JPanel cell = new JPanel();
            cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
            swatch.setAlignmentX(Component.CENTER_ALIGNMENT);
            cell.add(swatch);
            cell.add(label);
            row.add(cell);
        }
        return row;
    }

    private static String formatAlpha(AnalysisResult result) {
        if (!result.hasAlphaChannel()) {
            return Messages.get("common.no");
        }
        return Messages.get("dialog.analysis.result.alphaYes", result.transparentPixelPercent());
    }

    private static String formatGamut(GamutCheckResult gamut) {
        String status = gamut.hasSignificantOutOfGamut() ? Messages.get("common.yes") : Messages.get("common.no");
        return Messages.get("dialog.analysis.result.gamutDetail", status, gamut.outOfGamutPixelPercent());
    }

    private static String formatResolutionCheck(ResolutionCheck check) {
        String status = check.belowRecommended()
                ? Messages.get("dialog.analysis.result.resolutionBelow")
                : Messages.get("dialog.analysis.result.resolutionOk");
        return Messages.get("dialog.analysis.result.resolutionDetail", check.printWidthCm(), check.printHeightCm(),
                check.resultingHorizontalDpi(), check.resultingVerticalDpi(), check.minRecommendedDpi(), status);
    }

    private static String formatEdgeContinuity(EdgeContinuityResult continuity) {
        return Messages.get("dialog.analysis.result.continuityDetail", continuity.level(), continuity.overallAverageDifference());
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
        return orDefault(value, Messages.get("common.notAvailable"));
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return Messages.get("dialog.info.fileSize.bytes", bytes);
        }
        double kilobytes = bytes / 1024.0;
        if (kilobytes < 1024) {
            return Messages.get("dialog.info.fileSize.kilobytes", kilobytes);
        }
        return Messages.get("dialog.info.fileSize.megabytes", kilobytes / 1024.0);
    }

    private static String formatLastModified(ImageMetadata metadata) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Messages.getLocale())
                .withZone(java.time.ZoneId.systemDefault())
                .format(metadata.lastModified());
    }

    private static String formatDpi(ImageMetadata metadata) {
        if (!metadata.hasKnownDpi()) {
            return Messages.get("dialog.info.dimensions.resolutionUnknown", Messages.get("common.notAvailable"));
        }
        return Messages.get("dialog.info.dimensions.resolutionKnown", metadata.horizontalDpi(), metadata.verticalDpi());
    }

    private static String formatPhysicalSize(ImageMetadata metadata) {
        String suffix = metadata.hasKnownDpi()
                ? ""
                : Messages.get("dialog.info.dimensions.physicalSizeEstimatedSuffix", (int) metadata.assumedDpiForEstimate());
        return Messages.get("dialog.info.dimensions.physicalSizeValue",
                metadata.widthCm(), metadata.heightCm(), metadata.widthInches(), metadata.heightInches(), suffix);
    }

    private static String formatColorMode(ImageMetadata metadata) {
        ColorMode colorMode = metadata.colorMode();
        if (colorMode == null) {
            return Messages.get("common.notAvailable");
        }
        String base = Messages.get(switch (colorMode) {
            case RGB -> "colorMode.rgb";
            case GRAYSCALE -> "colorMode.grayscale";
            case CMYK -> "colorMode.cmyk";
            case INDEXED -> "colorMode.indexed";
            case OTHER -> "colorMode.other";
        });
        return metadata.colorModeHasAlpha() ? base + " " + Messages.get("colorMode.alphaSuffix") : base;
    }

    private static String formatBitDepth(Integer bitsPerChannel) {
        return bitsPerChannel != null
                ? Messages.get("dialog.info.color.bitDepthValue", bitsPerChannel)
                : Messages.get("common.notAvailable");
    }

    private static String formatOrientation(Integer orientationCode) {
        if (orientationCode == null) {
            return Messages.get("common.notAvailable");
        }
        String key = "exif.orientation." + orientationCode;
        String text = Messages.get(key);
        return text.equals(key) ? Messages.get("common.notAvailable") : text;
    }

    private static String formatCameraSource(ExifInfo exif) {
        String make = exif.cameraMake();
        String model = exif.cameraModel();
        if (make == null && model == null) {
            return Messages.get("common.notAvailable");
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
