package com.eltena.effectcore.effect;

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

    public static ScreenShaderType fromConfig(String value, String sourceDescription) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Missing shader type at " + sourceDescription + " -> shader.type"
            );
        }

        ScreenShaderType resolved = fromLayerConfig(value);
        if (resolved == NONE) {
            throw new IllegalStateException(
                "Unsupported shader type '" + value + "' at " + sourceDescription + " -> shader.type"
            );
        }
        return resolved;
    }

    public static ScreenShaderType fromLayerConfig(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }

        String normalized = value.trim()
            .toUpperCase(Locale.ROOT)
            .replace('-', '_');
        return switch (normalized) {
            case "MOTION_BLUR" -> MOTION_BLUR;
            case "MOTION_BLUR_FAKE" -> MOTION_BLUR_FAKE;
            default -> {
                try {
                    yield ScreenShaderType.valueOf(normalized);
                } catch (IllegalArgumentException ignored) {
                    yield NONE;
                }
            }
        };
    }
}
