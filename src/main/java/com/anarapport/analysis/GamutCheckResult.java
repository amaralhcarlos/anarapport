package com.anarapport.analysis;

/**
 * Approximate check for RGB colors that a standard CMYK print process would
 * likely be unable to reproduce faithfully. This is a saturation/brightness
 * heuristic (see ImageAnalyzer), not a colorimetric conversion against a real
 * CMYK ICC profile, since none ships with the JDK.
 */
public record GamutCheckResult(double outOfGamutPixelPercent, boolean hasSignificantOutOfGamut) {
}
