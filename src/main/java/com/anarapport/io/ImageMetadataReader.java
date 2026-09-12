package com.anarapport.io;

import com.anarapport.model.ColorMode;
import com.anarapport.model.ExifInfo;
import com.anarapport.model.ImageMetadata;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.plugins.jpeg.JPEGQTable;
import javax.imageio.stream.ImageInputStream;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Reads fast-to-obtain technical metadata about an image file (dimensions, DPI,
 * color mode/bit depth, embedded ICC profile, EXIF) without decoding pixel data.
 * Meant for a "file info" panel aimed at someone reworking the file elsewhere
 * (e.g. Photoshop), not for pixel-level image analysis.
 */
public final class ImageMetadataReader {

    private static final double MM_PER_INCH = 25.4;
    private static final double ASSUMED_DPI = 96.0;

    private ImageMetadataReader() {
    }

    public static ImageMetadata read(File file) throws IOException {
        try (ImageInputStream inputStream = ImageIO.createImageInputStream(file)) {
            if (inputStream == null) {
                throw new IOException("Unsupported or unreadable image file: " + file.getName());
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(inputStream);
            if (!readers.hasNext()) {
                throw new IOException("No ImageIO reader available for: " + file.getName());
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(inputStream, true, false);
                return readMetadata(reader, file);
            } finally {
                reader.dispose();
            }
        }
    }

    /**
     * Reads the luminance (qtableId=0) quantization table from a JPEG file, for
     * estimating its encoding quality. Returns null for non-JPEG files or if the
     * table isn't available. Used only by the on-demand "advanced analysis"
     * feature, so this is a separate, lightweight metadata-only read rather than
     * something {@link #read} always does.
     */
    public static int[] readJpegLuminanceQuantTable(File file) throws IOException {
        try (ImageInputStream inputStream = ImageIO.createImageInputStream(file)) {
            if (inputStream == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(inputStream);
            if (!readers.hasNext()) {
                return null;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(inputStream, true, false);
                if (!"JPEG".equalsIgnoreCase(reader.getFormatName())) {
                    return null;
                }
                return extractLuminanceQuantTable(safeImageMetadata(reader));
            } finally {
                reader.dispose();
            }
        }
    }

    private static int[] extractLuminanceQuantTable(IIOMetadata metadata) {
        if (metadata == null || !supportsFormat(metadata, "javax_imageio_jpeg_image_1.0")) {
            return null;
        }
        try {
            Node markerSequence = findMarkerSequence(metadata);
            if (markerSequence == null) {
                return null;
            }
            // On read, each quantization table appears as its own <dqt><dqtable/></dqt>
            // pair; qtableId="0" is the luminance table by JPEG convention.
            NodeList dqtNodes = markerSequence.getChildNodes();
            for (int i = 0; i < dqtNodes.getLength(); i++) {
                Node dqt = dqtNodes.item(i);
                if (!"dqt".equals(dqt.getNodeName())) {
                    continue;
                }
                NodeList tables = dqt.getChildNodes();
                for (int j = 0; j < tables.getLength(); j++) {
                    Node table = tables.item(j);
                    if (table instanceof IIOMetadataNode tableNode
                            && "0".equals(tableNode.getAttribute("qtableId"))
                            && tableNode.getUserObject() instanceof JPEGQTable quantTable) {
                        return quantTable.getTable();
                    }
                }
            }
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static ImageMetadata readMetadata(ImageReader reader, File file) throws IOException {
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        String format = reader.getFormatName().toUpperCase(Locale.ROOT);

        IIOMetadata metadata = safeImageMetadata(reader);

        DpiInfo dpi = metadata != null ? readDpi(metadata) : new DpiInfo(null, null);

        ImageTypeSpecifier rawType = safeRawImageType(reader);
        ColorModel colorModel = rawType != null ? rawType.getColorModel() : null;
        ColorMode colorMode = describeColorMode(colorModel);
        boolean colorModeHasAlpha = colorModel != null && colorModel.hasAlpha();
        Integer bitsPerChannel = describeBitDepth(colorModel);

        String iccProfileName = switch (format) {
            case "PNG" -> readPngIccProfileName(metadata);
            case "JPEG" -> readJpegIccProfileName(metadata, rawType);
            default -> null;
        };

        ExifInfo exif = "JPEG".equals(format) && metadata != null ? readExif(metadata) : ExifInfo.EMPTY;

        return new ImageMetadata(width, height, dpi.horizontal(), dpi.vertical(), ASSUMED_DPI,
                colorMode, colorModeHasAlpha, bitsPerChannel, iccProfileName, format, file.length(), file.getName(),
                file.getAbsolutePath(), Instant.ofEpochMilli(file.lastModified()), exif);
    }

    private static IIOMetadata safeImageMetadata(ImageReader reader) {
        try {
            return reader.getImageMetadata(0);
        } catch (IOException e) {
            return null;
        }
    }

    private static ImageTypeSpecifier safeRawImageType(ImageReader reader) {
        try {
            ImageTypeSpecifier type = reader.getRawImageType(0);
            if (type != null) {
                return type;
            }
            Iterator<ImageTypeSpecifier> types = reader.getImageTypes(0);
            return types.hasNext() ? types.next() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static ColorMode describeColorMode(ColorModel colorModel) {
        if (colorModel == null) {
            return null;
        }
        if (colorModel instanceof IndexColorModel) {
            return ColorMode.INDEXED;
        }
        return switch (colorModel.getColorSpace().getType()) {
            case ColorSpace.TYPE_RGB -> ColorMode.RGB;
            case ColorSpace.TYPE_GRAY -> ColorMode.GRAYSCALE;
            case ColorSpace.TYPE_CMYK -> ColorMode.CMYK;
            default -> ColorMode.OTHER;
        };
    }

    private static Integer describeBitDepth(ColorModel colorModel) {
        if (colorModel == null) {
            return null;
        }
        int[] componentSizes = colorModel.getComponentSize();
        return componentSizes.length > 0 ? componentSizes[0] : null;
    }

    private record DpiInfo(Double horizontal, Double vertical) {
    }

    // The standard ("javax_imageio_1.0") metadata format normalizes each plugin's
    // native resolution info (PNG pHYs, JFIF density, ...) into a Dimension/
    // Horizontal|VerticalPixelSize pair, in millimeters per pixel.
    private static DpiInfo readDpi(IIOMetadata metadata) {
        if (!metadata.isStandardMetadataFormatSupported()) {
            return new DpiInfo(null, null);
        }
        try {
            Node root = metadata.getAsTree("javax_imageio_1.0");
            Node dimension = findChild(root, "Dimension");
            if (dimension == null) {
                return new DpiInfo(null, null);
            }
            Double horizontalMm = readPixelSizeMm(dimension, "HorizontalPixelSize");
            Double verticalMm = readPixelSizeMm(dimension, "VerticalPixelSize");
            Double horizontalDpi = horizontalMm != null && horizontalMm > 0 ? MM_PER_INCH / horizontalMm : null;
            Double verticalDpi = verticalMm != null && verticalMm > 0 ? MM_PER_INCH / verticalMm : null;
            return new DpiInfo(horizontalDpi, verticalDpi);
        } catch (RuntimeException e) {
            return new DpiInfo(null, null);
        }
    }

    private static Double readPixelSizeMm(Node dimensionNode, String elementName) {
        Node element = findChild(dimensionNode, elementName);
        if (element == null || element.getAttributes() == null) {
            return null;
        }
        Node valueAttribute = element.getAttributes().getNamedItem("value");
        if (valueAttribute == null) {
            return null;
        }
        try {
            return Double.parseDouble(valueAttribute.getNodeValue());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // PNG's iCCP chunk name is exposed directly as an attribute of the native
    // metadata tree; the (compressed) profile bytes are not, so there is no
    // ICC_Profile to build for PNG -- just the embedded profile's own name.
    private static String readPngIccProfileName(IIOMetadata metadata) {
        if (metadata == null || !supportsFormat(metadata, "javax_imageio_png_1.0")) {
            return null;
        }
        try {
            Node root = metadata.getAsTree("javax_imageio_png_1.0");
            Node iccp = findChild(root, "iCCP");
            if (iccp == null || iccp.getAttributes() == null) {
                return null;
            }
            Node profileName = iccp.getAttributes().getNamedItem("profileName");
            return profileName != null ? profileName.getNodeValue() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // The JPEG plugin consumes APP2 ICC_PROFILE segments internally (they are
    // not exposed as raw "unknown" marker bytes, unlike APP1/Exif): it records
    // an empty <app2ICC/> marker under app0JFIF when one was present, and uses
    // the actual embedded profile to build the ColorSpace behind getRawImageType.
    private static String readJpegIccProfileName(IIOMetadata metadata, ImageTypeSpecifier rawType) {
        if (rawType == null || !hasJpegIccMarker(metadata)) {
            return null;
        }
        ColorSpace colorSpace = rawType.getColorModel().getColorSpace();
        return colorSpace instanceof ICC_ColorSpace iccColorSpace
                ? extractProfileDescription(iccColorSpace.getProfile())
                : null;
    }

    private static boolean hasJpegIccMarker(IIOMetadata metadata) {
        if (metadata == null || !supportsFormat(metadata, "javax_imageio_jpeg_image_1.0")) {
            return false;
        }
        try {
            Node root = metadata.getAsTree("javax_imageio_jpeg_image_1.0");
            Node jpegVariety = findChild(root, "JPEGvariety");
            Node app0Jfif = jpegVariety != null ? findChild(jpegVariety, "app0JFIF") : null;
            return app0Jfif != null && findChild(app0Jfif, "app2ICC") != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String extractProfileDescription(ICC_Profile profile) {
        try {
            byte[] tagData = profile.getData(ICC_Profile.icSigProfileDescriptionTag);
            if (tagData == null || tagData.length < 12) {
                return "Perfil ICC embutido (nome indisponível)";
            }
            String typeSignature = new String(tagData, 0, 4, StandardCharsets.US_ASCII);
            String description = switch (typeSignature) {
                case "desc" -> readLegacyIccDescription(tagData); // ICC v2 (icDescriptionType)
                case "mluc" -> readMultiLocalizedIccDescription(tagData); // ICC v4 (multiLocalizedUnicodeType)
                default -> null;
            };
            return description != null && !description.isBlank() ? description : "Perfil ICC embutido (nome indisponível)";
        } catch (RuntimeException e) {
            return "Perfil ICC embutido (nome indisponível)";
        }
    }

    private static String readLegacyIccDescription(byte[] tagData) {
        long asciiCount = readUInt32BE(tagData, 8);
        int length = (int) Math.max(0, asciiCount - 1); // exclude the null terminator
        int available = Math.min(length, tagData.length - 12);
        return available > 0 ? new String(tagData, 12, available, StandardCharsets.US_ASCII).trim() : null;
    }

    private static String readMultiLocalizedIccDescription(byte[] tagData) {
        if (tagData.length < 28) {
            return null;
        }
        // Only the first localized record is used (typically en/US), which is
        // enough for a display name in this panel.
        int recordLength = (int) readUInt32BE(tagData, 20);
        int recordOffset = (int) readUInt32BE(tagData, 24);
        int available = Math.min(recordLength, tagData.length - recordOffset);
        if (recordOffset < 0 || available <= 0 || recordOffset + available > tagData.length) {
            return null;
        }
        return new String(tagData, recordOffset, available, StandardCharsets.UTF_16BE).trim();
    }

    private static long readUInt32BE(byte[] data, int offset) {
        return ((data[offset] & 0xFFL) << 24) | ((data[offset + 1] & 0xFFL) << 16)
                | ((data[offset + 2] & 0xFFL) << 8) | (data[offset + 3] & 0xFFL);
    }

    private static ExifInfo readExif(IIOMetadata metadata) {
        if (!supportsFormat(metadata, "javax_imageio_jpeg_image_1.0")) {
            return ExifInfo.EMPTY;
        }
        try {
            Node markerSequence = findMarkerSequence(metadata);
            if (markerSequence == null) {
                return ExifInfo.EMPTY;
            }
            NodeList children = markerSequence.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (!(node instanceof IIOMetadataNode metadataNode) || !"225".equals(metadataNode.getAttribute("MarkerTag"))) {
                    continue;
                }
                if (!(metadataNode.getUserObject() instanceof byte[] data) || data.length < 6) {
                    continue;
                }
                if (isExifSignature(data)) {
                    return ExifReader.parse(Arrays.copyOfRange(data, 6, data.length));
                }
            }
            return ExifInfo.EMPTY;
        } catch (RuntimeException e) {
            return ExifInfo.EMPTY;
        }
    }

    private static boolean isExifSignature(byte[] data) {
        return data[0] == 'E' && data[1] == 'x' && data[2] == 'i' && data[3] == 'f' && data[4] == 0 && data[5] == 0;
    }

    private static Node findMarkerSequence(IIOMetadata metadata) {
        Node root = metadata.getAsTree("javax_imageio_jpeg_image_1.0");
        return findChild(root, "markerSequence");
    }

    private static boolean supportsFormat(IIOMetadata metadata, String formatName) {
        return Arrays.asList(metadata.getMetadataFormatNames()).contains(formatName);
    }

    private static Node findChild(Node parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (name.equals(child.getNodeName())) {
                return child;
            }
        }
        return null;
    }
}
