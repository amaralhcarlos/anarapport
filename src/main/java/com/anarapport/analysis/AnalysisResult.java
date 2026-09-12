package com.anarapport.analysis;

import java.util.List;

/**
 * Aggregate result of running {@link ImageAnalyzer} over a loaded image.
 * {@code estimatedJpegQuality} and {@code resolutionCheck} are null when not
 * applicable (not a JPEG; no print size was provided).
 */
public record AnalysisResult(int uniqueColorCount, boolean hasAlphaChannel, double transparentPixelPercent,
                              GamutCheckResult gamutCheck, Integer estimatedJpegQuality,
                              List<DominantColor> dominantColors, EdgeContinuityResult edgeContinuity,
                              ResolutionCheck resolutionCheck) {
}
