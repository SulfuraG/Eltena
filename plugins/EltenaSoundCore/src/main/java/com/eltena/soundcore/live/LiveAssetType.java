package com.eltena.soundcore.live;

import java.util.Locale;

public enum LiveAssetType {
    VOICE;

    public static LiveAssetType fromConfig(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Live asset type is required.");
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "VOICE" -> VOICE;
            default -> throw new IllegalArgumentException("Unsupported live asset type: " + value);
        };
    }

    public String payloadName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
