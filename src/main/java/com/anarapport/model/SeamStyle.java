package com.anarapport.model;

import com.anarapport.i18n.Messages;

/**
 * Visual style used to draw the subtle seam line between adjacent tile replicas.
 */
public enum SeamStyle {
    DARK_GRAY_SOLID("seamStyle.darkGray"),
    WHITE_SOLID("seamStyle.white"),
    GRAY_DASHED("seamStyle.dashedGray"),
    RED_HIGH_CONTRAST("seamStyle.redHighContrast");

    private final String messageKey;

    SeamStyle(String messageKey) {
        this.messageKey = messageKey;
    }

    @Override
    public String toString() {
        // Resolved on every call (not cached) so it reflects the current
        // language immediately after a runtime language switch.
        return Messages.get(messageKey);
    }
}
