package com.anarapport.model;

import com.anarapport.i18n.Messages;

/**
 * Identifies the repeat strategy used to tile the source image
 * (as in the textile/pattern-design notion of a "rapport").
 * More styles (brick, mirror, etc.) can be added here later.
 */
public enum RapportType {
    STRAIGHT("rapport.straight"),
    HALF_DROP("rapport.halfDrop"),
    MIRROR("rapport.mirror");

    private final String messageKey;

    RapportType(String messageKey) {
        this.messageKey = messageKey;
    }

    @Override
    public String toString() {
        // Resolved on every call (not cached) so it reflects the current
        // language immediately after a runtime language switch.
        return Messages.get(messageKey);
    }
}
