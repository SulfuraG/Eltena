package com.eltena.soundcore.audio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class OggDurationResolver {
    private OggDurationResolver() {
    }

    public static long resolveDurationMs(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return 0L;
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException exception) {
            return 0L;
        }

        CodecInfo codec = null;
        long lastGranulePosition = -1L;
        int index = 0;
        while (index <= bytes.length - 27) {
            if (!isOggPage(bytes, index)) {
                index++;
                continue;
            }

            int pageSegments = unsignedByte(bytes[index + 26]);
            int headerLength = 27 + pageSegments;
            if (index + headerLength > bytes.length) {
                break;
            }

            int bodyLength = 0;
            for (int segmentIndex = 0; segmentIndex < pageSegments; segmentIndex++) {
                bodyLength += unsignedByte(bytes[index + 27 + segmentIndex]);
            }

            int pageLength = headerLength + bodyLength;
            if (index + pageLength > bytes.length) {
                break;
            }

            long granulePosition = littleEndianLong(bytes, index + 6);
            if (granulePosition >= 0L) {
                lastGranulePosition = granulePosition;
            }

            if (codec == null) {
                codec = parseCodec(bytes, index + headerLength, bodyLength);
            }

            index += pageLength;
        }

        if (codec == null || codec.granuleRate() <= 0L || lastGranulePosition <= 0L) {
            return 0L;
        }

        double durationMs = (lastGranulePosition * 1000.0D) / codec.granuleRate();
        return Math.max(1L, Math.round(durationMs));
    }

    private static boolean isOggPage(byte[] bytes, int offset) {
        return bytes[offset] == 'O'
            && bytes[offset + 1] == 'g'
            && bytes[offset + 2] == 'g'
            && bytes[offset + 3] == 'S';
    }

    private static CodecInfo parseCodec(byte[] bytes, int bodyOffset, int bodyLength) {
        if (bodyLength >= 16
            && matches(bytes, bodyOffset, "OpusHead")) {
            return new CodecInfo(48_000L);
        }

        if (bodyLength >= 16
            && unsignedByte(bytes[bodyOffset]) == 0x01
            && matches(bytes, bodyOffset + 1, "vorbis")) {
            long sampleRate = littleEndianInt(bytes, bodyOffset + 12);
            return sampleRate > 0L ? new CodecInfo(sampleRate) : null;
        }

        return null;
    }

    private static boolean matches(byte[] bytes, int offset, String value) {
        if (offset < 0 || offset + value.length() > bytes.length) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (bytes[offset + index] != (byte) value.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static int unsignedByte(byte value) {
        return value & 0xFF;
    }

    private static long littleEndianInt(byte[] bytes, int offset) {
        return ((long) unsignedByte(bytes[offset]))
            | ((long) unsignedByte(bytes[offset + 1]) << 8)
            | ((long) unsignedByte(bytes[offset + 2]) << 16)
            | ((long) unsignedByte(bytes[offset + 3]) << 24);
    }

    private static long littleEndianLong(byte[] bytes, int offset) {
        return ((long) unsignedByte(bytes[offset]))
            | ((long) unsignedByte(bytes[offset + 1]) << 8)
            | ((long) unsignedByte(bytes[offset + 2]) << 16)
            | ((long) unsignedByte(bytes[offset + 3]) << 24)
            | ((long) unsignedByte(bytes[offset + 4]) << 32)
            | ((long) unsignedByte(bytes[offset + 5]) << 40)
            | ((long) unsignedByte(bytes[offset + 6]) << 48)
            | ((long) unsignedByte(bytes[offset + 7]) << 56);
    }

    private record CodecInfo(
        long granuleRate
    ) {
    }
}
