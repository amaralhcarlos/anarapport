package com.anarapport.app;

import com.anarapport.i18n.Messages;
import com.anarapport.io.ImageExporter;
import com.anarapport.io.ImageLoader;
import com.anarapport.io.ImageMetadataReader;
import com.anarapport.model.AppState;
import com.anarapport.model.ImageMetadata;
import com.anarapport.model.RapportType;
import com.anarapport.model.SeamStyle;
import com.anarapport.ui.ImageInfoDialog;
import com.anarapport.ui.ImagePanel;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

/**
 * Application entry point.
 */
public class Main {

    private static final String PREFS_KEY_LANGUAGE = "language";

    public static void main(String[] args) {
        // Swing components must be created on the Event Dispatch Thread
        SwingUtilities.invokeLater(Main::createAndShowGui);
    }

    private static void createAndShowGui() {
        Messages.setLocale(loadSavedLocale());

        AppState appState = new AppState();
        ImagePanel imagePanel = new ImagePanel();
        imagePanel.setGridSize(appState.getGridSize());
        imagePanel.setRapportType(appState.getRapportType());
        imagePanel.setShowTileSeams(appState.isShowTileSeams());
        imagePanel.setSeamStyle(appState.getSeamStyle());
        imagePanel.setCellOffsetXPercent(appState.getCellOffsetXPercent());
        imagePanel.setCellOffsetYPercent(appState.getCellOffsetYPercent());

        // Rebuilt in place on every language switch; kept as the same Map
        // instance so the property-change listener below (registered once)
        // keeps working against whatever buttons currently exist.
        Map<RapportType, JToggleButton> rapportModeButtons = new EnumMap<>(RapportType.class);

        // Keep the panel (and toolbar selection) in sync with the model whenever it changes
        appState.addPropertyChangeListener(event -> {
            switch (event.getPropertyName()) {
                case AppState.PROPERTY_IMAGE -> imagePanel.setImage(appState.getImage());
                case AppState.PROPERTY_GRID_SIZE -> imagePanel.setGridSize(appState.getGridSize());
                case AppState.PROPERTY_RAPPORT_TYPE -> {
                    imagePanel.setRapportType(appState.getRapportType());
                    JToggleButton button = rapportModeButtons.get(appState.getRapportType());
                    if (button != null) {
                        button.setSelected(true);
                    }
                }
                case AppState.PROPERTY_SHOW_SEAMS -> imagePanel.setShowTileSeams(appState.isShowTileSeams());
                case AppState.PROPERTY_SEAM_STYLE -> imagePanel.setSeamStyle(appState.getSeamStyle());
                case AppState.PROPERTY_CELL_OFFSET_X -> imagePanel.setCellOffsetXPercent(appState.getCellOffsetXPercent());
                case AppState.PROPERTY_CELL_OFFSET_Y -> imagePanel.setCellOffsetYPercent(appState.getCellOffsetYPercent());
                default -> { }
            }
        });

        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        // Keeps the (multi-row) controls area from being squeezed into wrapping
        // rows taller than what a FlowLayout row reports as its preferred size,
        // which would otherwise let the image panel overlap/hide the controls.
        frame.setMinimumSize(new Dimension(720, 520));
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());
        frame.add(imagePanel, BorderLayout.CENTER);

        Runnable rebuildChrome = () -> rebuildChrome(frame, appState, imagePanel, rapportModeButtons);
        rebuildChrome.run();
        // Menu-triggered, so this always runs on the EDT already; no invokeLater needed.
        Messages.addChangeListener(rebuildChrome);

        frame.setVisible(true);
    }

    /**
     * (Re)builds the frame's chrome -- title, menu bar, toolbar and controls
     * panel -- so a live language switch can refresh every piece of text
     * without touching {@code appState}/{@code imagePanel} (the loaded image,
     * zoom/pan, grid size, offsets and seam settings are left untouched).
     */
    private static void rebuildChrome(JFrame frame, AppState appState, ImagePanel imagePanel,
                                       Map<RapportType, JToggleButton> rapportModeButtons) {
        frame.setTitle(Messages.get("app.title"));

        rapportModeButtons.clear();
        JToolBar rapportModeToolBar = buildRapportModeToolBar(appState, rapportModeButtons);

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(rapportModeToolBar, BorderLayout.NORTH);
        northPanel.add(buildControlsPanel(appState), BorderLayout.SOUTH);

        BorderLayout layout = (BorderLayout) frame.getContentPane().getLayout();
        Component oldNorth = layout.getLayoutComponent(BorderLayout.NORTH);
        if (oldNorth != null) {
            frame.remove(oldNorth);
        }
        frame.add(northPanel, BorderLayout.NORTH);

        frame.setJMenuBar(buildMenuBar(frame, appState, imagePanel));

        frame.revalidate();
        frame.repaint();
    }

    private static Locale loadSavedLocale() {
        String tag = Preferences.userNodeForPackage(Main.class).get(PREFS_KEY_LANGUAGE, null);
        if (tag != null) {
            for (Locale supported : Messages.getSupportedLocales()) {
                if (supported.getLanguage().equals(tag)) {
                    return supported;
                }
            }
        }
        return Locale.ENGLISH;
    }

    private static void saveLocalePreference(Locale locale) {
        Preferences.userNodeForPackage(Main.class).put(PREFS_KEY_LANGUAGE, locale.getLanguage());
    }

    /**
     * Toolbar with one mutually-exclusive toggle button per RapportType, so the user
     * can switch modes at any time. Switching only updates AppState.rapportType; the
     * panel's current zoom/pan is untouched, so the grid redraws in place.
     */
    private static JToolBar buildRapportModeToolBar(AppState appState, Map<RapportType, JToggleButton> buttonsByType) {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        ButtonGroup group = new ButtonGroup();
        for (RapportType type : RapportType.values()) {
            JToggleButton button = new JToggleButton(type.toString());
            button.setSelected(type == appState.getRapportType());
            button.addActionListener(event -> appState.setRapportType(type));
            group.add(button);
            toolBar.add(button);
            buttonsByType.put(type, button);
        }
        return toolBar;
    }

    private static JMenuBar buildMenuBar(JFrame parentFrame, AppState appState, ImagePanel imagePanel) {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu(Messages.get("menu.file"));
        JMenuItem openImageItem = new JMenuItem(Messages.get("menu.file.open"));
        openImageItem.addActionListener(event -> openImage(parentFrame, appState, openImageItem));
        fileMenu.add(openImageItem);

        fileMenu.addSeparator();

        JMenuItem exportCompositionItem = new JMenuItem(Messages.get("menu.file.export"));
        exportCompositionItem.addActionListener(event -> exportComposition(parentFrame, imagePanel, exportCompositionItem));
        fileMenu.add(exportCompositionItem);

        menuBar.add(fileMenu);

        JMenu imageMenu = new JMenu(Messages.get("menu.image"));
        JMenuItem imageInfoItem = new JMenuItem(Messages.get("menu.image.info"));
        imageInfoItem.addActionListener(event -> showImageInfo(parentFrame, appState));
        imageMenu.add(imageInfoItem);
        menuBar.add(imageMenu);

        menuBar.add(buildLanguageMenu());

        return menuBar;
    }

    private static JMenu buildLanguageMenu() {
        JMenu languageMenu = new JMenu(Messages.get("menu.language"));
        ButtonGroup group = new ButtonGroup();
        for (Locale locale : Messages.getSupportedLocales()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(languageDisplayName(locale));
            item.setSelected(locale.equals(Messages.getLocale()));
            item.addActionListener(event -> {
                Messages.setLocale(locale);
                saveLocalePreference(locale);
            });
            group.add(item);
            languageMenu.add(item);
        }
        return languageMenu;
    }

    // Each language names itself, regardless of the currently active UI language.
    private static String languageDisplayName(Locale locale) {
        if (Locale.ENGLISH.equals(locale)) {
            return "English";
        }
        if ("pt".equals(locale.getLanguage())) {
            return "Português";
        }
        return locale.getDisplayName(locale);
    }

    private static void showImageInfo(JFrame parentFrame, AppState appState) {
        ImageMetadata metadata = appState.getImageMetadata();
        if (metadata == null) {
            JOptionPane.showMessageDialog(parentFrame, Messages.get("dialog.noImage.message"),
                    Messages.get("dialog.noImage.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        ImageInfoDialog.show(parentFrame, metadata, appState.getImage());
    }

    /**
     * Two explicit rows (grid/offset geometry, then the seam overlay) stacked
     * vertically, rather than one long FlowLayout row. A single FlowLayout row
     * with this many controls reports a one-row preferred height even when the
     * window is too narrow and it actually wraps to two rows when laid out --
     * that mismatch is what let the second row overlap/hide behind the image
     * panel at the default (non-maximized) window size.
     */
    private static JPanel buildControlsPanel(AppState appState) {
        JPanel controlsPanel = new JPanel();
        controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.Y_AXIS));
        controlsPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JLabel gridSizeLabel = new JLabel(Messages.get("control.gridSize"));
        SpinnerNumberModel gridSizeModel = new SpinnerNumberModel(
                appState.getGridSize(), AppState.MIN_GRID_SIZE, AppState.MAX_GRID_SIZE, 1);
        JSpinner gridSizeSpinner = new JSpinner(gridSizeModel);
        gridSizeSpinner.addChangeListener(event -> appState.setGridSize((Integer) gridSizeSpinner.getValue()));

        JLabel offsetXLabel = new JLabel(Messages.get("control.offsetX"));
        SpinnerNumberModel offsetXModel = new SpinnerNumberModel(appState.getCellOffsetXPercent(),
                AppState.MIN_CELL_OFFSET_PERCENT, AppState.MAX_CELL_OFFSET_PERCENT, 1);
        JSpinner offsetXSpinner = new JSpinner(offsetXModel);
        offsetXSpinner.addChangeListener(event -> appState.setCellOffsetXPercent((Integer) offsetXSpinner.getValue()));

        JLabel offsetYLabel = new JLabel(Messages.get("control.offsetY"));
        SpinnerNumberModel offsetYModel = new SpinnerNumberModel(appState.getCellOffsetYPercent(),
                AppState.MIN_CELL_OFFSET_PERCENT, AppState.MAX_CELL_OFFSET_PERCENT, 1);
        JSpinner offsetYSpinner = new JSpinner(offsetYModel);
        offsetYSpinner.addChangeListener(event -> appState.setCellOffsetYPercent((Integer) offsetYSpinner.getValue()));

        JPanel geometryRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        geometryRow.add(gridSizeLabel);
        geometryRow.add(gridSizeSpinner);
        geometryRow.add(offsetXLabel);
        geometryRow.add(offsetXSpinner);
        geometryRow.add(offsetYLabel);
        geometryRow.add(offsetYSpinner);

        JCheckBox showSeamsCheckBox = new JCheckBox(Messages.get("control.showSeams"), appState.isShowTileSeams());
        JComboBox<SeamStyle> seamStyleCombo = new JComboBox<>(SeamStyle.values());
        seamStyleCombo.setSelectedItem(appState.getSeamStyle());
        seamStyleCombo.setEnabled(appState.isShowTileSeams());

        showSeamsCheckBox.addActionListener(event -> {
            boolean selected = showSeamsCheckBox.isSelected();
            appState.setShowTileSeams(selected);
            seamStyleCombo.setEnabled(selected);
        });
        seamStyleCombo.addActionListener(event ->
                appState.setSeamStyle((SeamStyle) seamStyleCombo.getSelectedItem()));

        JPanel seamRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        seamRow.add(showSeamsCheckBox);
        seamRow.add(seamStyleCombo);

        controlsPanel.add(geometryRow);
        controlsPanel.add(seamRow);
        return controlsPanel;
    }

    /**
     * Loading decodes an image file and reads its technical metadata (disk I/O +
     * decoding + header parsing), which can take a noticeable while for large
     * files, so both run off the EDT in a single SwingWorker; only the file
     * chooser and the final AppState update happen on the EDT. The metadata is
     * only shown on demand via "Imagem > Informações da imagem...".
     */
    private static void openImage(JFrame parentFrame, AppState appState, JMenuItem triggeringItem) {
        JFileChooser fileChooser = new JFileChooser();
        // Every format the JDK's own ImageIO can decode without extra plugins
        fileChooser.setFileFilter(new FileNameExtensionFilter(Messages.get("fileChooser.openFilter"),
                "png", "jpg", "jpeg", "tif", "tiff", "bmp", "gif"));

        int result = fileChooser.showOpenDialog(parentFrame);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selectedFile = fileChooser.getSelectedFile();
        triggeringItem.setEnabled(false);
        new SwingWorker<LoadedImage, Void>() {
            @Override
            protected LoadedImage doInBackground() throws IOException {
                BufferedImage image = ImageLoader.load(selectedFile);
                ImageMetadata metadata = ImageMetadataReader.read(selectedFile);
                return new LoadedImage(image, metadata);
            }

            @Override
            protected void done() {
                triggeringItem.setEnabled(true);
                try {
                    LoadedImage loaded = get();
                    appState.setImage(loaded.image());
                    appState.setImageMetadata(loaded.metadata());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    JOptionPane.showMessageDialog(parentFrame,
                            Messages.get("error.loadImage", e.getCause().getMessage()),
                            Messages.get("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private record LoadedImage(BufferedImage image, ImageMetadata metadata) {
    }

    /**
     * Rendering the composition (potentially many tile draws) and encoding/writing
     * it to disk are both CPU/IO-heavy, so they run off the EDT in a SwingWorker.
     * The snapshot is captured synchronously on the EDT first, so the background
     * thread never touches ImagePanel's fields directly.
     */
    private static void exportComposition(JFrame parentFrame, ImagePanel imagePanel, JMenuItem triggeringItem) {
        ImagePanel.CompositionSnapshot snapshot = imagePanel.captureComposition();
        if (snapshot == null) {
            JOptionPane.showMessageDialog(parentFrame, Messages.get("dialog.noComposition.message"),
                    Messages.get("dialog.noComposition.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        FileNameExtensionFilter pngFilter = new FileNameExtensionFilter(Messages.get("fileChooser.pngFilter"), "png");
        FileNameExtensionFilter jpegFilter = new FileNameExtensionFilter(Messages.get("fileChooser.jpegFilter"), "jpg", "jpeg");

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.addChoosableFileFilter(pngFilter);
        fileChooser.addChoosableFileFilter(jpegFilter);
        fileChooser.setFileFilter(pngFilter);

        int result = fileChooser.showSaveDialog(parentFrame);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        String formatName = ((FileNameExtensionFilter) fileChooser.getFileFilter()).getExtensions()[0];
        File targetFile = withExtension(fileChooser.getSelectedFile(), formatName);

        triggeringItem.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws IOException {
                ImageExporter.save(snapshot.render(), targetFile, formatName);
                return null;
            }

            @Override
            protected void done() {
                triggeringItem.setEnabled(true);
                try {
                    get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    JOptionPane.showMessageDialog(parentFrame,
                            Messages.get("error.saveImage", e.getCause().getMessage()),
                            Messages.get("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static File withExtension(File file, String extension) {
        String lowerCaseName = file.getName().toLowerCase();
        boolean alreadyHasExtension = lowerCaseName.endsWith("." + extension)
                || ("jpg".equals(extension) && lowerCaseName.endsWith(".jpeg"));
        return alreadyHasExtension ? file : new File(file.getParentFile(), file.getName() + "." + extension);
    }
}
