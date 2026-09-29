package com.eltena.effect.effect;

import java.util.Locale;

public enum ScreenShaderType {
    NONE,
    RADIAL_BLUR,
    SONIC_PRESSURE_BURST,
    MOTION_BLUR,
    MOTION_BLUR_FAKE,
    PRESSURE_DISTORTION,
    SHOCKWAVE_REFRACTION,
    HEAT_REFRACTION,
    UNDERWATER_REFRACTION,
    CHROMATIC_ABERRATION,
    COLOR_GRADE,
    DESATURATION,
    WHITEOUT,
    DARK_VIGNETTE_SOFT,
    NOISE_FOG,
    SCREEN_SHAKE_WARP,
    GHOSTING,
    TIME_WARP,
    FRACTURE_DISTORTION;

    public static ScreenShaderType fromPayload(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }

        String normalized = value.trim()
            .toUpperCase(Locale.ROOT)
            .replace('-', '_');
        try {
            return ScreenShaderType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
