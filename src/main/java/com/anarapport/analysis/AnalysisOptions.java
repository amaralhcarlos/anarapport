package com.anarapport.analysis;

/**
 * User-provided inputs for the advanced analysis: an optional intended print
 * size (both null when not provided, in which case no resolution check is
 * computed) and the minimum DPI considered acceptable for that print size.
 */
public record AnalysisOptions(Double printWidthCm, Double printHeightCm, int minRecommendedDpi) {

    public static final int DEFAULT_MIN_RECOMMENDED_DPI = 300;

    public boolean hasPrintSize() {
        return printWidthCm != null && printHeightCm != null;
    }
}
