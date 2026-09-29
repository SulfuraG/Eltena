package com.eltena.effect.effect;

import java.util.ArrayList;
import java.util.List;

public record ScreenEffectPayload(
    String effectId,
    ScreenVisualPattern visualPattern,
    long durationMs,
    double strength,
    long fadeInMs,
    long fadeOutMs,
    boolean debugLog,
    Color color,
    Shader shader
) {
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
    }

    public record Shader(
        boolean enabled,
        ScreenShaderType type,
        double strength,
        int samples,
        double radius,
        double chromaticOffset,
        double pressureDistortion,
        List<Layer> layers
    ) {
        public Shader {
            type = type == null ? ScreenShaderType.NONE : type;
            layers = layers == null ? List.of() : List.copyOf(layers);
        }

        public static Shader disabled() {
            return new Shader(
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
                case MOTION_BLUR, MOTION_BLUR_FAKE, CHROMATIC_ABERRATION, COLOR_GRADE, DESATURATION, WHITEOUT, DARK_VIGNETTE_SOFT, NOISE_FOG, SCREEN_SHAKE_WARP, GHOSTING, TIME_WARP, FRACTURE_DISTORTION -> strength > 0.0D;
                case NONE -> false;
            };
        }

        public boolean active() {
            return enabled && (!layers.isEmpty() || legacyActive());
        }

        public List<Layer> layersOrLegacy() {
            if (!layers.isEmpty()) {
                return layers;
            }
            if (!legacyActive()) {
                return List.of();
            }

            List<Layer> fallbackLayers = new ArrayList<>();
            ScreenShaderType legacyType = type == ScreenShaderType.NONE ? ScreenShaderType.RADIAL_BLUR : type;
            fallbackLayers.add(new Layer(
                legacyType,
                strength,
                samples,
                radius,
                8.0D,
                1.2D,
                false,
                ScreenShaderDirection.OUTWARD,
                0.0D,
                1.0D,
                ScreenShaderCurve.EASE_OUT,
                new Color(1.0D, 1.0D, 1.0D, 0.0D),
                0.0D,
                0.0D,
                1.0D,
                pressureDistortion,
                0.0D,
                0.0D,
                0.0D,
                legacyType == ScreenShaderType.SONIC_PRESSURE_BURST ? 24 : 0,
                legacyType == ScreenShaderType.SONIC_PRESSURE_BURST ? 0.85D : 0.0D,
                legacyType == ScreenShaderType.SONIC_PRESSURE_BURST ? 12.0D : 0.0D,
                legacyType == ScreenShaderType.SONIC_PRESSURE_BURST ? 0.35D : 0.0D,
                legacyType == ScreenShaderType.SONIC_PRESSURE_BURST ? 1.0D : 0.0D,
                chromaticOffset
            ));
            if (chromaticOffset > 0.0D) {
                fallbackLayers.add(new Layer(
                    ScreenShaderType.CHROMATIC_ABERRATION,
                    chromaticOffset,
                    0,
                    0.0D,
                    0.0D,
                    0.0D,
                    true,
                    ScreenShaderDirection.OUTWARD,
                    0.0D,
                    1.0D,
                    ScreenShaderCurve.EASE_OUT,
                    new Color(1.0D, 1.0D, 1.0D, 0.0D),
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D
                ));
            }
            return List.copyOf(fallbackLayers);
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
                color = color == null ? new Color(1.0D, 1.0D, 1.0D, 0.0D) : color;
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
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
