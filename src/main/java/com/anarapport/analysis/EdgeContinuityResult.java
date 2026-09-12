package com.anarapport.analysis;

/**
 * How well the motif's opposite edges would line up if tiled side by side:
 * the average per-pixel color distance between the left/right columns and
 * between the top/bottom rows (0 = identical, higher = more mismatched).
 */
public record EdgeContinuityResult(double horizontalAverageDifference, double verticalAverageDifference,
                                    double overallAverageDifference, ContinuityLevel level) {
}
