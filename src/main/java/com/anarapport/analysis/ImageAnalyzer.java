package com.anarapport.analysis;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Pixel-level analysis of a loaded image: color count, transparency, an
 * approximate CMYK gamut warning, dominant colors, edge continuity, an
 * optional print-resolution check, and (for JPEG) an estimated quality.
 * Pure computation over a BufferedImage/int[] quantization table -- no file
 * or Swing dependency, so it is reusable from any caller.
 */
public class ImageAnalyzer {

    // Rule-of-thumb thresholds for the edge continuity score, in Euclidean
    // RGB distance: below GOOD_THRESHOLD is imperceptible, above
    // ATTENTION_THRESHOLD is a clearly visible seam.
    private static final double CONTINUITY_GOOD_THRESHOLD = 15.0;
    private static final double CONTINUITY_ATTENTION_THRESHOLD = 40.0;

    // Heuristic for the approximate CMYK gamut warning: standard press CMYK
    // (e.g. SWOP/GRACoL) typically cannot reproduce very saturated, bright
    // colors, most notably vivid blues/cyans and greens.
    private static final float GAMUT_SATURATION_THRESHOLD = 0.85f;
    private static final float GAMUT_BRIGHTNESS_THRESHOLD = 0.55f;
    private static final float GAMUT_HUE_RANGE_START_DEGREES = 90f;
    private static final float GAMUT_HUE_RANGE_END_DEGREES = 260f;
    private static final double GAMUT_SIGNIFICANT_PERCENT = 1.0;

    private static final int DOMINANT_COLOR_COUNT = 8;

    public AnalysisResult analyze(BufferedImage image, int[] jpegLuminanceQuantTable,
                                   AnalysisOptions options, IntConsumer onProgress) {
        int width = image.getWidth();
        int height = image.getHeight();
        long totalPixels = (long) width * height;
        boolean hasAlpha = image.getColorModel().hasAlpha();

        Map<Integer, Long> colorCounts = new HashMap<>();
        long transparentPixels = 0;

        int[] row = new int[width];
        int progressReportEveryRows = Math.max(1, height / 100);
        for (int y = 0; y < height; y++) {
            image.getRGB(0, y, width, 1, row, 0, width);
            for (int x = 0; x < width; x++) {
                int argb = row[x];
                if (hasAlpha && ((argb >>> 24) & 0xFF) < 255) {
                    transparentPixels++;
                }
                colorCounts.merge(argb & 0xFFFFFF, 1L, Long::sum);
            }
            if (onProgress != null && y % progressReportEveryRows == 0) {
                onProgress.accept((int) (60.0 * y / height));
            }
        }

        double transparentPercent = hasAlpha && totalPixels > 0 ? 100.0 * transparentPixels / totalPixels : 0.0;
        List<DominantColor> dominantColors = findDominantColors(colorCounts, totalPixels);
        if (onProgress != null) {
            onProgress.accept(70);
        }

        GamutCheckResult gamutCheck = checkGamut(colorCounts, totalPixels);
        if (onProgress != null) {
            onProgress.accept(85);
        }

        EdgeContinuityResult edgeContinuity = computeEdgeContinuity(image);
        if (onProgress != null) {
            onProgress.accept(95);
        }

        Integer estimatedJpegQuality = jpegLuminanceQuantTable != null
                ? estimateJpegQuality(jpegLuminanceQuantTable)
                : null;
        ResolutionCheck resolutionCheck = options.hasPrintSize()
                ? computeResolutionCheck(width, height, options)
                : null;

        if (onProgress != null) {
            onProgress.accept(100);
        }

        return new AnalysisResult(colorCounts.size(), hasAlpha, transparentPercent, gamutCheck,
                estimatedJpegQuality, dominantColors, edgeContinuity, resolutionCheck);
    }

    private List<DominantColor> findDominantColors(Map<Integer, Long> colorCounts, long totalPixels) {
        return colorCounts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(DOMINANT_COLOR_COUNT)
                .map(entry -> new DominantColor(entry.getKey(), entry.getValue(),
                        totalPixels > 0 ? 100.0 * entry.getValue() / totalPixels : 0.0))
                .toList();
    }

    private GamutCheckResult checkGamut(Map<Integer, Long> colorCounts, long totalPixels) {
        long outOfGamutPixels = 0;
        for (Map.Entry<Integer, Long> entry : colorCounts.entrySet()) {
            if (isLikelyOutOfCmykGamut(entry.getKey())) {
                outOfGamutPixels += entry.getValue();
            }
        }
        double percent = totalPixels > 0 ? 100.0 * outOfGamutPixels / totalPixels : 0.0;
        return new GamutCheckResult(percent, percent > GAMUT_SIGNIFICANT_PERCENT);
    }

    private boolean isLikelyOutOfCmykGamut(int rgb) {
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        float hueDegrees = hsb[0] * 360f;
        float saturation = hsb[1];
        float brightness = hsb[2];
        if (saturation < GAMUT_SATURATION_THRESHOLD || brightness < GAMUT_BRIGHTNESS_THRESHOLD) {
            return false;
        }
        return hueDegrees >= GAMUT_HUE_RANGE_START_DEGREES && hueDegrees <= GAMUT_HUE_RANGE_END_DEGREES;
    }

    private EdgeContinuityResult computeEdgeContinuity(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        double horizontalDiffSum = 0;
        for (int y = 0; y < height; y++) {
            horizontalDiffSum += colorDistance(image.getRGB(0, y), image.getRGB(width - 1, y));
        }
        double horizontalAverage = height > 0 ? horizontalDiffSum / height : 0;

        double verticalDiffSum = 0;
        for (int x = 0; x < width; x++) {
            verticalDiffSum += colorDistance(image.getRGB(x, 0), image.getRGB(x, height - 1));
        }
        double verticalAverage = width > 0 ? verticalDiffSum / width : 0;

        double overall = (horizontalAverage + verticalAverage) / 2.0;
        ContinuityLevel level = overall < CONTINUITY_GOOD_THRESHOLD ? ContinuityLevel.GOOD
                : overall < CONTINUITY_ATTENTION_THRESHOLD ? ContinuityLevel.ATTENTION
                : ContinuityLevel.HIGH_DISCONTINUITY;

        return new EdgeContinuityResult(horizontalAverage, verticalAverage, overall, level);
    }

    private static double colorDistance(int rgbA, int rgbB) {
        int deltaRed = ((rgbA >> 16) & 0xFF) - ((rgbB >> 16) & 0xFF);
        int deltaGreen = ((rgbA >> 8) & 0xFF) - ((rgbB >> 8) & 0xFF);
        int deltaBlue = (rgbA & 0xFF) - (rgbB & 0xFF);
        return Math.sqrt((double) deltaRed * deltaRed + (double) deltaGreen * deltaGreen + (double) deltaBlue * deltaBlue);
    }

    private ResolutionCheck computeResolutionCheck(int pixelWidth, int pixelHeight, AnalysisOptions options) {
        double widthInches = options.printWidthCm() / 2.54;
        double heightInches = options.printHeightCm() / 2.54;
        double dpiWidth = widthInches > 0 ? pixelWidth / widthInches : 0;
        double dpiHeight = heightInches > 0 ? pixelHeight / heightInches : 0;
        boolean below = dpiWidth < options.minRecommendedDpi() || dpiHeight < options.minRecommendedDpi();
        return new ResolutionCheck(options.printWidthCm(), options.printHeightCm(), dpiWidth, dpiHeight,
                options.minRecommendedDpi(), below);
    }

    // Estimates JPEG quality from its luminance quantization table by comparing
    // it against the standard IJG quality-50 base table and inverting the
    // scaling formula the JPEG standard uses to derive a table from a quality
    // level. Encoders that don't derive their tables from the IJG baseline
    // (e.g. some Photoshop exports) will produce a less accurate estimate.
    private Integer estimateJpegQuality(int[] luminanceTable) {
        int[] baseTable = STANDARD_LUMINANCE_QUALITY_50_TABLE;
        if (luminanceTable.length != baseTable.length) {
            return null;
        }
        double totalRatio = 0;
        int count = 0;
        for (int i = 0; i < baseTable.length; i++) {
            if (baseTable[i] > 0) {
                totalRatio += (double) luminanceTable[i] / baseTable[i];
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        double scaleFactor = (totalRatio / count) * 100.0;
        double quality = scaleFactor <= 100 ? 5000.0 / scaleFactor : (200.0 - scaleFactor) / 2.0;
        return (int) Math.round(Math.max(1, Math.min(100, quality)));
    }

    // The standard IJG luminance quantization table at quality 50, used as the
    // baseline for scaling to any other quality level (and, here, for reversing
    // that scaling to estimate the quality from an arbitrary table).
    private static final int[] STANDARD_LUMINANCE_QUALITY_50_TABLE = {
            16, 11, 10, 16, 24, 40, 51, 61,
            12, 12, 14, 19, 26, 58, 60, 55,
            14, 13, 16, 24, 40, 57, 69, 56,
            14, 17, 22, 29, 51, 87, 80, 62,
            18, 22, 37, 56, 68, 109, 103, 77,
            24, 35, 55, 64, 81, 104, 113, 92,
            49, 64, 78, 87, 103, 121, 120, 101,
            72, 92, 95, 98, 112, 100, 103, 99
    };
}
