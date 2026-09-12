package com.anarapport.analysis;

/**
 * One entry of the dominant-color palette: an RGB value (no alpha) and how
 * much of the image it covers.
 */
public record DominantColor(int rgb, long pixelCount, double percentOfImage) {

    public int red() {
        return (rgb >> 16) & 0xFF;
    }

    public int green() {
        return (rgb >> 8) & 0xFF;
    }

    public int blue() {
        return rgb & 0xFF;
    }

    public String toHex() {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }
}
