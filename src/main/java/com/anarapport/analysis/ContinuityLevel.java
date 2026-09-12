package com.anarapport.analysis;

import com.anarapport.i18n.Messages;

/**
 * Qualitative bucket for the edge-continuity score (how well the motif's
 * opposite edges would line up when tiled side by side).
 */
public enum ContinuityLevel {
    GOOD("continuity.good"),
    ATTENTION("continuity.attention"),
    HIGH_DISCONTINUITY("continuity.highDiscontinuity");

    private final String messageKey;

    ContinuityLevel(String messageKey) {
        this.messageKey = messageKey;
    }

    @Override
    public String toString() {
        // Resolved on every call (not cached) so it reflects the current
        // language immediately after a runtime language switch.
        return Messages.get(messageKey);
    }
}
