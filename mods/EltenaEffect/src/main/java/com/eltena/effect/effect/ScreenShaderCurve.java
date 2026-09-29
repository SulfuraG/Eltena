package com.eltena.effect.effect;

import java.util.Locale;

public enum ScreenShaderCurve {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT;

    public static ScreenShaderCurve fromPayload(String value) {
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

    public double apply(double value) {
        double clamped = Math.max(0.0D, Math.min(1.0D, value));
        return switch (this) {
            case LINEAR -> clamped;
            case EASE_IN -> clamped * clamped;
            case EASE_OUT -> 1.0D - Math.pow(1.0D - clamped, 2.0D);
            case EASE_IN_OUT -> clamped < 0.5D
                ? 2.0D * clamped * clamped
                : 1.0D - (Math.pow(-2.0D * clamped + 2.0D, 2.0D) * 0.5D);
        };
    }
}
