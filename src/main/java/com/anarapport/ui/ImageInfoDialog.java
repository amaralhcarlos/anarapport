package com.anarapport.ui;

import com.anarapport.analysis.AnalysisOptions;
import com.anarapport.analysis.AnalysisResult;
import com.anarapport.analysis.DominantColor;
import com.anarapport.analysis.EdgeContinuityResult;
import com.anarapport.analysis.GamutCheckResult;
import com.anarapport.analysis.ImageAnalyzer;
import com.anarapport.analysis.ResolutionCheck;
import com.anarapport.io.ImageMetadataReader;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

/**
 * Read-only dialog listing an image file's technical metadata, aimed at someone
 * who will rework the file elsewhere (e.g. Photoshop): dimensions, resolution,
 * color mode/bit depth, ICC profile, file info and EXIF, when available. Also
 * offers an on-demand "advanced analysis" section (pixel-level metrics), run
 * via {@link ImageAnalyzer} in a background SwingWorker with a progress bar.
 */
public final class ImageInfoDialog {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter LAST_MODIFIED_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", PT_BR);
    private static final String NOT_AVAILABLE = "Não informado";

    private ImageInfoDialog() {
    }

    public static void show(JFrame owner, ImageMetadata metadata, BufferedImage image) {
        JDialog dialog = new JDialog(owner, "Informações da imagem", false);
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

    /**
     * Builds the "Análise avançada" section: print-size/DPI-threshold inputs, an
     * "Analisar imagem" button, a progress bar, and a results area that starts
     * empty and is replaced after each run. Nothing here runs until the button
     * is clicked, since the analysis is pixel-level and can take a while.
     */
    private static JPanel buildAdvancedAnalysisSection(JDialog dialog, ImageMetadata metadata, BufferedImage image) {
        JPanel section = new JPanel(new GridBagLayout());
        section.setBorder(BorderFactory.createEmptyBorder(0, 16, 12, 16));

        int row = 0;
        row = addSectionTitle(section, "Análise avançada", row);

        GridBagConstraints fullWidth = new GridBagConstraints();
        fullWidth.gridx = 0;
        fullWidth.gridy = row++;
        fullWidth.gridwidth = 2;
        fullWidth.anchor = GridBagConstraints.NORTHWEST;
        fullWidth.insets = new Insets(0, 0, 8, 0);
        JLabel explanation = new JLabel("<html>Processa a imagem pixel a pixel; pode levar alguns segundos em"
                + " imagens grandes, por isso só roda quando solicitado.</html>");
        section.add(explanation, fullWidth);

        JCheckBox checkResolution = new JCheckBox("Verificar resolução para impressão em:");
        SpinnerNumberModel widthModel = new SpinnerNumberModel(10.0, 0.1, 1000.0, 0.5);
        JSpinner widthSpinner = new JSpinner(widthModel);
        SpinnerNumberModel heightModel = new SpinnerNumberModel(10.0, 0.1, 1000.0, 0.5);
        JSpinner heightSpinner = new JSpinner(heightModel);
        JComboBox<String> unitCombo = new JComboBox<>(new String[] {"cm", "polegadas"});
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

        JLabel minDpiLabel = new JLabel("DPI mínimo recomendado:");
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

        JButton analyzeButton = new JButton("Analisar imagem");
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
        resultsPanel.add(new JLabel("Nenhuma análise executada ainda."));
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
            boolean isInches = "polegadas".equals(unitCombo.getSelectedItem());
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
                    JOptionPane.showMessageDialog(dialog, "Falha ao analisar a imagem: " + e.getCause().getMessage(),
                            "Erro", JOptionPane.ERROR_MESSAGE);
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
        row = addRow(textGrid, "Cores únicas:", String.valueOf(result.uniqueColorCount()), row);
        row = addRow(textGrid, "Canal alfa (transparência):", formatAlpha(result), row);
        row = addRow(textGrid, "Cores fora do gamut CMYK:", formatGamut(result.gamutCheck()), row);
        if (result.resolutionCheck() != null) {
            row = addRow(textGrid, "Resolução para impressão:", formatResolutionCheck(result.resolutionCheck()), row);
        }
        if (result.estimatedJpegQuality() != null) {
            row = addRow(textGrid, "Qualidade JPEG estimada:", result.estimatedJpegQuality() + "%", row);
        }
        addRow(textGrid, "Continuidade de borda:", formatEdgeContinuity(result.edgeContinuity()), row);
        container.add(textGrid);

        JLabel paletteLabel = new JLabel("Cores dominantes:");
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
                    + "," + color.blue() + ")<br>" + String.format(PT_BR, "%.1f%%", color.percentOfImage()) + "</html>");
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
            return "Não";
        }
        return String.format(PT_BR, "Sim (%.1f%% da imagem é transparente)", result.transparentPixelPercent());
    }

    private static String formatGamut(GamutCheckResult gamut) {
        String status = gamut.hasSignificantOutOfGamut() ? "Sim" : "Não";
        return String.format(PT_BR, "%s (%.1f%% dos pixels — estimativa aproximada, sem perfil ICC CMYK real)",
                status, gamut.outOfGamutPixelPercent());
    }

    private static String formatResolutionCheck(ResolutionCheck check) {
        String status = check.belowRecommended() ? "abaixo do recomendado" : "adequada";
        return String.format(PT_BR, "%.1f x %.1f cm → %.0f x %.0f DPI (mínimo recomendado: %d DPI) — %s",
                check.printWidthCm(), check.printHeightCm(), check.resultingHorizontalDpi(),
                check.resultingVerticalDpi(), check.minRecommendedDpi(), status);
    }

    private static String formatEdgeContinuity(EdgeContinuityResult continuity) {
        return String.format(PT_BR, "%s (score: %.1f)", continuity.level(), continuity.overallAverageDifference());
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
