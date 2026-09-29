package com.eltena.effectcore.sequence;

import java.util.Locale;

public enum SyncDurationMode {
    MATCH;

    public static SyncDurationMode fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return MATCH;
        }
        String normalized = value.trim()
            .replace('-', '_')
            .toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MATCH" -> MATCH;
            default -> throw new IllegalArgumentException(
                "Unsupported sync duration mode: " + value
            );
        };
    }
}
