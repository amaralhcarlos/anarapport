package com.anarapport.io;

import com.anarapport.model.ExifInfo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Minimal TIFF/EXIF IFD parser: reads only the handful of tags a technical
 * "file info" panel needs (camera make/model, capture date, orientation) from
 * the raw TIFF structure carried inside a JPEG's APP1 (Exif) segment.
 */
final class ExifReader {

    private static final int TAG_MAKE = 0x010F;
    private static final int TAG_MODEL = 0x0110;
    private static final int TAG_ORIENTATION = 0x0112;
    private static final int TAG_DATETIME = 0x0132;
    private static final int TAG_EXIF_IFD_POINTER = 0x8769;
    private static final int TAG_DATETIME_ORIGINAL = 0x9003;

    private static final int TYPE_ASCII = 2;
    private static final int TYPE_SHORT = 3;

    private ExifReader() {
    }

    /** {@code tiffData} is the raw TIFF structure, with the leading "Exif\0\0" already stripped. */
    static ExifInfo parse(byte[] tiffData) {
        try {
            ByteOrder order = tiffData[0] == 'M' ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
            ByteBuffer buffer = ByteBuffer.wrap(tiffData).order(order);

            if ((buffer.getShort(2) & 0xFFFF) != 0x002A) {
                return ExifInfo.EMPTY;
            }

            int ifd0Offset = buffer.getInt(4);
            String make = null;
            String model = null;
            Integer orientationCode = null;
            String dateTime = null;
            Integer exifIfdOffset = null;

            int entryCount = buffer.getShort(ifd0Offset) & 0xFFFF;
            for (int i = 0; i < entryCount; i++) {
                int entryOffset = ifd0Offset + 2 + i * 12;
                int tag = buffer.getShort(entryOffset) & 0xFFFF;
                int type = buffer.getShort(entryOffset + 2) & 0xFFFF;
                long count = buffer.getInt(entryOffset + 4) & 0xFFFFFFFFL;
                int valueOffset = entryOffset + 8;

                switch (tag) {
                    case TAG_MAKE -> make = readAscii(buffer, type, count, valueOffset);
                    case TAG_MODEL -> model = readAscii(buffer, type, count, valueOffset);
                    case TAG_DATETIME -> dateTime = readAscii(buffer, type, count, valueOffset);
                    case TAG_ORIENTATION -> orientationCode = readShort(buffer, type, valueOffset);
                    case TAG_EXIF_IFD_POINTER -> exifIfdOffset = buffer.getInt(valueOffset);
                    default -> { }
                }
            }

            String dateTimeOriginal = (exifIfdOffset != null && exifIfdOffset > 0 && exifIfdOffset < tiffData.length)
                    ? readDateTimeOriginal(buffer, exifIfdOffset)
                    : null;

            String captureDate = dateTimeOriginal != null ? dateTimeOriginal : dateTime;
            make = trimOrNull(make);
            model = trimOrNull(model);
            if (make == null && model == null && captureDate == null && orientationCode == null) {
                return ExifInfo.EMPTY;
            }
            return new ExifInfo(make, model, captureDate, orientationCode);
        } catch (RuntimeException e) {
            // Any malformed/unexpected structure -> treat as "no EXIF" rather than propagate
            return ExifInfo.EMPTY;
        }
    }

    private static String readDateTimeOriginal(ByteBuffer buffer, int ifdOffset) {
        int entryCount = buffer.getShort(ifdOffset) & 0xFFFF;
        for (int i = 0; i < entryCount; i++) {
            int entryOffset = ifdOffset + 2 + i * 12;
            if ((buffer.getShort(entryOffset) & 0xFFFF) == TAG_DATETIME_ORIGINAL) {
                int type = buffer.getShort(entryOffset + 2) & 0xFFFF;
                long count = buffer.getInt(entryOffset + 4) & 0xFFFFFFFFL;
                return readAscii(buffer, type, count, entryOffset + 8);
            }
        }
        return null;
    }

    private static String readAscii(ByteBuffer buffer, int type, long count, int valueOffset) {
        if (type != TYPE_ASCII || count <= 1) {
            return null;
        }
        int length = (int) count - 1; // exclude the null terminator
        int dataOffset = count <= 4 ? valueOffset : buffer.getInt(valueOffset);
        if (dataOffset < 0 || dataOffset + length > buffer.capacity()) {
            return null;
        }
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = buffer.get(dataOffset + i);
        }
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static int readShort(ByteBuffer buffer, int type, int valueOffset) {
        return type == TYPE_SHORT ? buffer.getShort(valueOffset) & 0xFFFF : 0;
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
