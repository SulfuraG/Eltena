package com.eltena.effect.effect;

import java.util.Locale;

public enum EarthquakeShakePattern {
    SWAY,
    JITTER,
    HEAVY_RUMBLE,
    SHOCKWAVE,
    PULSE;

    public static EarthquakeShakePattern fromPayload(String value) {
        if (value == null || value.isBlank()) {
            return SWAY;
        }

        try {
            return EarthquakeShakePattern.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return SWAY;
        }
    }
}
