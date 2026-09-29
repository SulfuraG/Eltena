package com.eltena.effectcore.effect;

import java.util.Locale;

public enum ScreenShaderCurve {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT;

    public static ScreenShaderCurve fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return EASE_OUT;
        }

        String normalized = value.trim()
            .toUpperCase(Locale.ROOT)
            .replace('-', '_');
        try {
            return ScreenShaderCurve.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return EASE_OUT;
        }
    }
}
