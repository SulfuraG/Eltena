package com.eltena.effectcore.effect;

import java.util.Locale;

public enum ScreenShaderDirection {
    OUTWARD,
    INWARD,
    LEFT,
    RIGHT,
    UP,
    DOWN,
    DIAGONAL_LEFT,
    DIAGONAL_RIGHT;

    public static ScreenShaderDirection fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return OUTWARD;
        }

        String normalized = value.trim()
            .toUpperCase(Locale.ROOT)
            .replace('-', '_');
        try {
            return ScreenShaderDirection.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return OUTWARD;
        }
    }
}
