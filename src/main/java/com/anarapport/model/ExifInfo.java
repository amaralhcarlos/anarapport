package com.anarapport.model;

/**
 * A handful of EXIF tags relevant to a technical "file info" panel. Any field
 * may be null when the file carries no EXIF data, or that particular tag is
 * absent from it. {@code orientationCode} is the raw EXIF orientation value
 * (1-8, per the TIFF/EXIF spec) rather than display text, so the UI layer can
 * localize it.
 */
public record ExifInfo(String cameraMake, String cameraModel, String captureDate, Integer orientationCode) {

    public static final ExifInfo EMPTY = new ExifInfo(null, null, null, null);

    public boolean isEmpty() {
        return cameraMake == null && cameraModel == null && captureDate == null && orientationCode == null;
    }
}
