package com.anarapport.calibration;

import oshi.SystemInfo;
import oshi.hardware.Display;
import oshi.util.EdidUtil;

import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * Converts between pixels and physical centimeters for the user's screen, so
 * a loaded image can eventually be shown at its real physical size.
 *
 * <p>{@code java.awt.Toolkit.getScreenResolution()} cannot be trusted for
 * this: on most platforms it always reports a fixed 96 DPI regardless of the
 * actual monitor. Instead this tries to read the primary monitor's real
 * physical width from its EDID (via the OSHI hardware library) and combine
 * it with the screen's actual pixel resolution ({@link GraphicsDevice}).
 * When that fails -- or the user wants to correct it -- a manual
 * calibration (measuring an on-screen bar with a physical ruler, see
 * {@code ScreenCalibrationDialog}) is used instead. Whichever value was last
 * established is persisted per user via {@link Preferences}, together with
 * which of the two methods produced it.
 */
public final class ScreenCalibration {

    public enum Source { NONE, AUTOMATIC, MANUAL }

    private static final String PREFS_KEY_PIXELS_PER_CM = "pixelsPerCm";
    private static final String PREFS_KEY_SOURCE = "source";

    private static final double CM_PER_INCH = 2.54;
    private static final double MIN_VALID_DIAGONAL_INCHES = 5.0;
    private static final double MAX_VALID_DIAGONAL_INCHES = 100.0;

    private ScreenCalibration() {
    }

    public static boolean isCalibrated() {
        return getSource() != Source.NONE;
    }

    public static Source getSource() {
        String raw = prefs().get(PREFS_KEY_SOURCE, Source.NONE.name());
        try {
            return Source.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return Source.NONE;
        }
    }

    /** Empty when the screen has never been calibrated, automatically or manually. */
    public static Optional<Double> getPixelsPerCm() {
        if (getSource() == Source.NONE) {
            return Optional.empty();
        }
        double value = prefs().getDouble(PREFS_KEY_PIXELS_PER_CM, -1);
        return value > 0 ? Optional.of(value) : Optional.empty();
    }

    /**
     * Attempts automatic hardware detection, but only if the screen isn't
     * already calibrated by either method -- an existing calibration (in
     * particular a manual one the user deliberately entered) is never
     * silently overwritten by this. Returns whether it actually applied a
     * newly detected value.
     */
    public static boolean autoDetectAndSaveIfMissing() {
        if (isCalibrated()) {
            return false;
        }
        Double detected = detectPixelsPerCmFromHardware();
        if (detected == null) {
            return false;
        }
        save(detected, Source.AUTOMATIC);
        return true;
    }

    public static void saveManualCalibration(double pixelsPerCm) {
        save(pixelsPerCm, Source.MANUAL);
    }

    /**
     * Best-effort automatic detection: the primary monitor's real pixel width
     * (from {@link GraphicsDevice}) divided by its physical width in
     * centimeters, decoded from its EDID via OSHI. Isolated in its own
     * package-private method so it's easy to stub out in tests or replace if
     * OSHI's detection proves unreliable on some platform.
     *
     * <p>OSHI's display list has no id that reliably cross-references a
     * specific AWT {@code GraphicsDevice}, so this simply takes the first
     * display OSHI reports -- fine for the common single-monitor case, less
     * so on a multi-monitor setup where OSHI's first display might not be the
     * AWT default screen device used below.
     */
    static Double detectPixelsPerCmFromHardware() {
        try {
            List<Display> displays = new SystemInfo().getHardware().getDisplays();
            if (displays.isEmpty()) {
                return null;
            }
            byte[] edid = displays.get(0).getEdid();
            if (edid == null) {
                return null;
            }

            int horizontalCm = EdidUtil.getHcm(edid);
            int verticalCm = EdidUtil.getVcm(edid);
            if (!isPhysicalSizePlausible(horizontalCm, verticalCm)) {
                return null;
            }

            int pixelWidth = primaryScreenPixelWidth();
            if (pixelWidth <= 0) {
                return null;
            }

            return pixelWidth / (double) horizontalCm;
        } catch (RuntimeException | LinkageError e) {
            // Any hardware-access failure (missing native lib, unsupported OS,
            // no EDID exposed, ...) just means automatic detection isn't
            // available here; the caller falls back to manual calibration.
            return null;
        }
    }

    private static int primaryScreenPixelWidth() {
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        DisplayMode displayMode = device.getDisplayMode();
        return displayMode.getWidth();
    }

    private static boolean isPhysicalSizePlausible(int horizontalCm, int verticalCm) {
        if (horizontalCm <= 0 || verticalCm <= 0) {
            return false;
        }
        double diagonalInches = Math.sqrt((double) horizontalCm * horizontalCm + (double) verticalCm * verticalCm) / CM_PER_INCH;
        return diagonalInches >= MIN_VALID_DIAGONAL_INCHES && diagonalInches <= MAX_VALID_DIAGONAL_INCHES;
    }

    private static void save(double pixelsPerCm, Source source) {
        Preferences prefs = prefs();
        prefs.putDouble(PREFS_KEY_PIXELS_PER_CM, pixelsPerCm);
        prefs.put(PREFS_KEY_SOURCE, source.name());
    }

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(ScreenCalibration.class);
    }
}
