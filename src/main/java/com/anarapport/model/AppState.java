package com.anarapport.model;

import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Holds the application's mutable state, such as the currently loaded source image
 * and the current rapport rendering settings. Notifies listeners whenever the state
 * changes, so the UI layer can react to it.
 */
public class AppState {

    public static final String PROPERTY_IMAGE = "image";
    public static final String PROPERTY_GRID_SIZE = "gridSize";
    public static final String PROPERTY_RAPPORT_TYPE = "rapportType";
    public static final String PROPERTY_SHOW_SEAMS = "showTileSeams";
    public static final String PROPERTY_SEAM_STYLE = "seamStyle";
    public static final String PROPERTY_CELL_OFFSET_X = "cellOffsetXPercent";
    public static final String PROPERTY_CELL_OFFSET_Y = "cellOffsetYPercent";

    public static final int MIN_GRID_SIZE = 3;
    public static final int MAX_GRID_SIZE = 9;
    public static final int DEFAULT_GRID_SIZE = 3;

    // Percent of the motif's own size: negative overlaps cells, positive spaces
    // them apart, 0 (default) reproduces the original edge-to-edge tiling.
    public static final int MIN_CELL_OFFSET_PERCENT = -50;
    public static final int MAX_CELL_OFFSET_PERCENT = 100;
    public static final int DEFAULT_CELL_OFFSET_PERCENT = 0;

    private final PropertyChangeSupport support = new PropertyChangeSupport(this);
    private BufferedImage image;
    private int gridSize = DEFAULT_GRID_SIZE;
    private RapportType rapportType = RapportType.STRAIGHT;
    private boolean showTileSeams = true;
    private SeamStyle seamStyle = SeamStyle.DARK_GRAY_SOLID;
    private int cellOffsetXPercent = DEFAULT_CELL_OFFSET_PERCENT;
    private int cellOffsetYPercent = DEFAULT_CELL_OFFSET_PERCENT;

    public BufferedImage getImage() {
        return image;
    }

    public void setImage(BufferedImage image) {
        BufferedImage old = this.image;
        this.image = image;
        support.firePropertyChange(PROPERTY_IMAGE, old, image);
    }

    public int getGridSize() {
        return gridSize;
    }

    public void setGridSize(int gridSize) {
        int clamped = Math.max(MIN_GRID_SIZE, Math.min(MAX_GRID_SIZE, gridSize));
        int old = this.gridSize;
        this.gridSize = clamped;
        support.firePropertyChange(PROPERTY_GRID_SIZE, old, clamped);
    }

    public RapportType getRapportType() {
        return rapportType;
    }

    public void setRapportType(RapportType rapportType) {
        RapportType old = this.rapportType;
        this.rapportType = rapportType;
        support.firePropertyChange(PROPERTY_RAPPORT_TYPE, old, rapportType);
    }

    public boolean isShowTileSeams() {
        return showTileSeams;
    }

    public void setShowTileSeams(boolean showTileSeams) {
        boolean old = this.showTileSeams;
        this.showTileSeams = showTileSeams;
        support.firePropertyChange(PROPERTY_SHOW_SEAMS, old, showTileSeams);
    }

    public SeamStyle getSeamStyle() {
        return seamStyle;
    }

    public void setSeamStyle(SeamStyle seamStyle) {
        SeamStyle old = this.seamStyle;
        this.seamStyle = seamStyle;
        support.firePropertyChange(PROPERTY_SEAM_STYLE, old, seamStyle);
    }

    public int getCellOffsetXPercent() {
        return cellOffsetXPercent;
    }

    public void setCellOffsetXPercent(int cellOffsetXPercent) {
        int clamped = clampCellOffsetPercent(cellOffsetXPercent);
        int old = this.cellOffsetXPercent;
        this.cellOffsetXPercent = clamped;
        support.firePropertyChange(PROPERTY_CELL_OFFSET_X, old, clamped);
    }

    public int getCellOffsetYPercent() {
        return cellOffsetYPercent;
    }

    public void setCellOffsetYPercent(int cellOffsetYPercent) {
        int clamped = clampCellOffsetPercent(cellOffsetYPercent);
        int old = this.cellOffsetYPercent;
        this.cellOffsetYPercent = clamped;
        support.firePropertyChange(PROPERTY_CELL_OFFSET_Y, old, clamped);
    }

    private static int clampCellOffsetPercent(int percent) {
        return Math.max(MIN_CELL_OFFSET_PERCENT, Math.min(MAX_CELL_OFFSET_PERCENT, percent));
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }
}
