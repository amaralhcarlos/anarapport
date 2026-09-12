package com.anarapport.app;

import com.anarapport.io.ImageLoader;
import com.anarapport.model.AppState;
import com.anarapport.model.RapportType;
import com.anarapport.ui.ImagePanel;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Application entry point.
 */
public class Main {

    public static void main(String[] args) {
        // Swing components must be created on the Event Dispatch Thread
        SwingUtilities.invokeLater(Main::createAndShowGui);
    }

    private static void createAndShowGui() {
        AppState appState = new AppState();
        ImagePanel imagePanel = new ImagePanel();
        imagePanel.setGridSize(appState.getGridSize());
        imagePanel.setRapportType(appState.getRapportType());

        // Keep the panel in sync with the model whenever it changes
        appState.addPropertyChangeListener(event -> {
            switch (event.getPropertyName()) {
                case AppState.PROPERTY_IMAGE -> imagePanel.setImage(appState.getImage());
                case AppState.PROPERTY_GRID_SIZE -> imagePanel.setGridSize(appState.getGridSize());
                case AppState.PROPERTY_RAPPORT_TYPE -> imagePanel.setRapportType(appState.getRapportType());
                default -> { }
            }
        });

        JFrame frame = new JFrame("AnaRapport");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());
        frame.setJMenuBar(buildMenuBar(frame, appState));
        frame.add(buildControlsPanel(appState), BorderLayout.NORTH);
        frame.add(imagePanel, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    private static JMenuBar buildMenuBar(JFrame parentFrame, AppState appState) {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("Arquivo");
        JMenuItem openImageItem = new JMenuItem("Abrir imagem");
        openImageItem.addActionListener(event -> openImage(parentFrame, appState));
        fileMenu.add(openImageItem);

        menuBar.add(fileMenu);
        return menuBar;
    }

    private static JPanel buildControlsPanel(AppState appState) {
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlsPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JLabel gridSizeLabel = new JLabel("Tamanho da grade:");
        SpinnerNumberModel gridSizeModel = new SpinnerNumberModel(
                appState.getGridSize(), AppState.MIN_GRID_SIZE, AppState.MAX_GRID_SIZE, 1);
        JSpinner gridSizeSpinner = new JSpinner(gridSizeModel);
        gridSizeSpinner.addChangeListener(event -> appState.setGridSize((Integer) gridSizeSpinner.getValue()));

        JLabel rapportTypeLabel = new JLabel("Modo de rapport:");
        JComboBox<RapportType> rapportTypeCombo = new JComboBox<>(RapportType.values());
        rapportTypeCombo.setSelectedItem(appState.getRapportType());
        rapportTypeCombo.addActionListener(event ->
                appState.setRapportType((RapportType) rapportTypeCombo.getSelectedItem()));

        controlsPanel.add(gridSizeLabel);
        controlsPanel.add(gridSizeSpinner);
        controlsPanel.add(rapportTypeLabel);
        controlsPanel.add(rapportTypeCombo);
        return controlsPanel;
    }

    private static void openImage(JFrame parentFrame, AppState appState) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("Image files (PNG, JPEG)", "png", "jpg", "jpeg"));

        int result = fileChooser.showOpenDialog(parentFrame);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selectedFile = fileChooser.getSelectedFile();
        try {
            BufferedImage image = ImageLoader.load(selectedFile);
            appState.setImage(image);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(parentFrame, "Could not load image: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
