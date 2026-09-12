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

    public static final int MIN_GRID_SIZE = 3;
    public static final int MAX_GRID_SIZE = 9;
    public static final int DEFAULT_GRID_SIZE = 3;

    private final PropertyChangeSupport support = new PropertyChangeSupport(this);
    private BufferedImage image;
    private int gridSize = DEFAULT_GRID_SIZE;
    private RapportType rapportType = RapportType.STRAIGHT;

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

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }
}
