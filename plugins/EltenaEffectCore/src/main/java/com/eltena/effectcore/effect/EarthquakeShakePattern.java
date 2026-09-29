package com.eltena.effectcore.effect;

import java.util.Locale;

public enum EarthquakeShakePattern {
    SWAY,
    JITTER,
    HEAVY_RUMBLE,
    SHOCKWAVE,
    PULSE;

    public static EarthquakeShakePattern fromConfig(String value, String sourceDescription) {
        if (value == null || value.isBlank()) {
            return SWAY;
        }

        try {
            return EarthquakeShakePattern.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                "Unknown shake-pattern at " + sourceDescription + " -> " + value,
                exception
            );
        }
    }
}
