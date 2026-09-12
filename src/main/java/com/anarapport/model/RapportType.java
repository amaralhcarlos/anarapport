package com.anarapport.model;

/**
 * Identifies the repeat strategy used to tile the source image
 * (as in the textile/pattern-design notion of a "rapport").
 * More styles (brick, mirror, etc.) can be added here later.
 */
public enum RapportType {
    STRAIGHT("Reto"),
    HALF_DROP("Half Drop"),
    MIRROR("Espelhado");

    private final String displayName;

    RapportType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
