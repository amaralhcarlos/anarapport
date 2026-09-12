package com.anarapport.model;

/**
 * Visual style used to draw the subtle seam line between adjacent tile replicas.
 */
public enum SeamStyle {
    DARK_GRAY_SOLID("Cinza escuro"),
    WHITE_SOLID("Branco"),
    GRAY_DASHED("Tracejada cinza"),
    RED_HIGH_CONTRAST("Vermelho (alto contraste)");

    private final String displayName;

    SeamStyle(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
