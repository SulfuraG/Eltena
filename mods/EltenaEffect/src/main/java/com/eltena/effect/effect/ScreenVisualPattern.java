package com.eltena.effect.effect;

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
    TIME_DISTORTION,
    UNKNOWN;

    public static ScreenVisualPattern fromPayload(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }

        try {
            return ScreenVisualPattern.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return UNKNOWN;
        }
    }
}
