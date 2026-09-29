package com.eltena.effectcore.effect;

import java.util.Locale;

public enum DistanceFalloffType {
    LINEAR;

    public static DistanceFalloffType fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return LINEAR;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "LINEAR" -> LINEAR;
            default -> throw new IllegalArgumentException("Unsupported distance falloff type: " + value);
        };
    }
}
