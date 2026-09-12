package com.anarapport.model;

import java.time.Instant;

/**
 * Fast-to-obtain technical metadata about a loaded image file, aimed at someone
 * who will rework the file in an external tool (e.g. Photoshop). Fields that
 * could not be determined from the file are null; callers should present a
 * clear "not available"/estimated fallback rather than omitting them.
 * {@code colorMode} is a plain enum (not display text) so this stays
 * locale-agnostic; the UI layer localizes it.
 */
public record ImageMetadata(int pixelWidth, int pixelHeight, Double horizontalDpi, Double verticalDpi,
                             double assumedDpiForEstimate, ColorMode colorMode, boolean colorModeHasAlpha,
                             Integer bitsPerChannel, String iccProfileName, String fileFormat, long fileSizeBytes,
                             String fileName, String filePath, Instant lastModified, ExifInfo exif) {

    private static final double MM_PER_INCH = 25.4;

    public boolean hasKnownDpi() {
        return horizontalDpi != null && verticalDpi != null;
    }

    private double effectiveHorizontalDpi() {
        return horizontalDpi != null ? horizontalDpi : assumedDpiForEstimate;
    }

    private double effectiveVerticalDpi() {
        return verticalDpi != null ? verticalDpi : assumedDpiForEstimate;
    }

    public double widthInches() {
        return pixelWidth / effectiveHorizontalDpi();
    }

    public double heightInches() {
        return pixelHeight / effectiveVerticalDpi();
    }

    public double widthCm() {
        return widthInches() * MM_PER_INCH / 10.0;
    }

    public double heightCm() {
        return heightInches() * MM_PER_INCH / 10.0;
    }
}
