package com.eltena.effectcore.effect;

import java.util.List;

public record ScreenShaderDefinition(
    boolean enabled,
    ScreenShaderType type,
    double strength,
    int samples,
    double radius,
    double chromaticOffset,
    double pressureDistortion,
    List<Layer> layers
) {
    public ScreenShaderDefinition {
        type = type == null ? ScreenShaderType.NONE : type;
        layers = layers == null ? List.of() : List.copyOf(layers);
    }

    public static ScreenShaderDefinition disabled() {
        return new ScreenShaderDefinition(
            false,
            ScreenShaderType.NONE,
            0.0D,
            0,
            0.0D,
            0.0D,
            0.0D,
            List.of()
        );
    }

    public boolean legacyActive() {
        if (!enabled) {
            return false;
        }

        return switch (type) {
            case RADIAL_BLUR -> strength > 0.0D && samples > 0 && radius > 0.0D;
            case SONIC_PRESSURE_BURST -> strength > 0.0D;
            case PRESSURE_DISTORTION, SHOCKWAVE_REFRACTION, HEAT_REFRACTION, UNDERWATER_REFRACTION -> Math.max(strength, pressureDistortion) > 0.0D;
            case CHROMATIC_ABERRATION, COLOR_GRADE, DESATURATION, WHITEOUT, DARK_VIGNETTE_SOFT, NOISE_FOG, SCREEN_SHAKE_WARP, GHOSTING, TIME_WARP, FRACTURE_DISTORTION, MOTION_BLUR, MOTION_BLUR_FAKE -> strength > 0.0D;
            case NONE -> false;
        };
    }

    public boolean active() {
        return enabled && (legacyActive() || !layers.isEmpty());
    }

    public record Color(
        double r,
        double g,
        double b,
        double a
    ) {
        public Color {
            r = clamp(r, 0.0D, 1.0D);
            g = clamp(g, 0.0D, 1.0D);
            b = clamp(b, 0.0D, 1.0D);
            a = clamp(a, 0.0D, 1.0D);
        }

        public static Color transparent() {
            return new Color(1.0D, 1.0D, 1.0D, 0.0D);
        }
    }

    public record Layer(
        ScreenShaderType type,
        double strength,
        int samples,
        double radius,
        double frequency,
        double speed,
        boolean edgeOnly,
        ScreenShaderDirection direction,
        double startProgress,
        double endProgress,
        ScreenShaderCurve curve,
        Color color,
        double noiseStrength,
        double wobble,
        double blurScale,
        double refractionStrength,
        double desaturation,
        double smear,
        double pulse,
        int count,
        double length,
        double sharpness,
        double centerBias,
        double edgeBias,
        double chromaticOffset
    ) {
        public Layer {
            type = type == null ? ScreenShaderType.NONE : type;
            samples = Math.max(0, Math.min(24, samples));
            radius = Math.max(0.0D, radius);
            frequency = Math.max(0.0D, frequency);
            speed = Math.max(0.0D, speed);
            direction = direction == null ? ScreenShaderDirection.OUTWARD : direction;
            startProgress = clamp(startProgress, 0.0D, 1.0D);
            endProgress = clamp(endProgress, 0.0D, 1.0D);
            curve = curve == null ? ScreenShaderCurve.EASE_OUT : curve;
            color = color == null ? Color.transparent() : color;
            noiseStrength = Math.max(0.0D, noiseStrength);
            wobble = Math.max(0.0D, wobble);
            blurScale = Math.max(0.0D, blurScale);
            refractionStrength = Math.max(0.0D, refractionStrength);
            desaturation = Math.max(0.0D, desaturation);
            smear = Math.max(0.0D, smear);
            pulse = Math.max(0.0D, pulse);
            count = Math.max(0, Math.min(128, count));
            length = Math.max(0.0D, length);
            sharpness = Math.max(0.0D, sharpness);
            centerBias = Math.max(0.0D, centerBias);
            edgeBias = Math.max(0.0D, edgeBias);
            chromaticOffset = Math.max(0.0D, chromaticOffset);
        }

        public boolean active() {
            return type != ScreenShaderType.NONE
                && strength > 0.0D
                && endProgress > startProgress;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
