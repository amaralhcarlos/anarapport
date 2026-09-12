package com.anarapport.analysis;

/**
 * Result of comparing the image's pixel dimensions against a user-intended
 * print size: the DPI that size would actually produce, versus the minimum
 * the user considers acceptable.
 */
public record ResolutionCheck(double printWidthCm, double printHeightCm, double resultingHorizontalDpi,
                               double resultingVerticalDpi, int minRecommendedDpi, boolean belowRecommended) {
}
