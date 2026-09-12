package com.anarapport.model;

/**
 * Coarse color model classification for a loaded file. Kept as a plain enum
 * (no display text) so it stays locale-agnostic; the UI layer looks up the
 * text for the current language.
 */
public enum ColorMode {
    RGB,
    GRAYSCALE,
    CMYK,
    INDEXED,
    OTHER
}
