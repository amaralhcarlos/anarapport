package com.anarapport.analysis;

/**
 * Qualitative bucket for the edge-continuity score (how well the motif's
 * opposite edges would line up when tiled side by side).
 */
public enum ContinuityLevel {
    GOOD("Boa continuidade"),
    ATTENTION("Atenção"),
    HIGH_DISCONTINUITY("Descontinuidade alta");

    private final String displayName;

    ContinuityLevel(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
