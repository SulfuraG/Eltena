package com.eltena.effectcore.effect;

import java.util.Locale;

public enum ScreenVisualPattern {
    ROAR_DISTORTION,
    SHOCKWAVE_RING,
    HIT_STOP_FEEL,
    BLOOD_PRESSURE,
    DARK_PRESSURE,
    WORLD_EVENT_WARNING,
    DIMENSION_SHIFT,
    VOID_COLLAPSE,
    HEAT_HAZE,
    BLIZZARD_WHITEOUT,
    UNDERWATER_PRESSURE,
    ENRAGE_AURA,
    FEAR_WAVE,
    TIME_DISTORTION;

    public static ScreenVisualPattern fromConfig(String value, String sourceDescription) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Missing visual-pattern at " + sourceDescription + " -> visual-pattern"
            );
        }

        try {
            return ScreenVisualPattern.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                "Unsupported visual-pattern at " + sourceDescription + " -> visual-pattern: " + value,
                exception
            );
        }
    }
}
