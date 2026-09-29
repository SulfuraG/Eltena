package com.eltena.effect.client.virtualcamera;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record VirtualCameraConfig(
    boolean enabled,
    boolean playerCameraEnabled,
    String defaultPreset,
    String activePreset,
    long sceneStartTimeMs,
    Map<String, WorldPosition> resolvedEntityLookAts,
    Map<String, Preset> presets
) {
    public VirtualCameraConfig {
        resolvedEntityLookAts = Collections.unmodifiableMap(new LinkedHashMap<>(
            resolvedEntityLookAts == null ? Map.of() : resolvedEntityLookAts
        ));
        presets = Collections.unmodifiableMap(new LinkedHashMap<>(presets == null ? Map.of() : presets));
    }

    public static VirtualCameraConfig defaults() {
        return new VirtualCameraConfig(false, false, "", "", 0L, Map.of(), Map.of());
    }

    public String resolvedActivePreset() {
        if (activePreset != null) {
            Preset selected = presets.get(activePreset);
            if (selected != null && selected.enabled()) {
                return selected.id();
            }
        }

        if (defaultPreset != null) {
            Preset defaultEntry = presets.get(defaultPreset);
            if (defaultEntry != null && defaultEntry.enabled()) {
                return defaultEntry.id();
            }
        }

        for (Preset preset : presets.values()) {
            if (preset.enabled()) {
                return preset.id();
            }
        }
        return null;
    }

    public Preset activePresetDefinition() {
        String resolved = resolvedActivePreset();
        if (resolved == null) {
            return null;
        }
        return presets.get(resolved);
    }

    public record Preset(
        String id,
        String displayName,
        boolean enabled,
        String mode,
        RelativePosition relativePosition,
        WorldPosition worldPosition,
        Rotation rotation,
        Movement movement,
        Smoothing smoothing,
        Behavior behavior,
        List<Keyframe> keyframes
    ) {
        public Preset {
            keyframes = List.copyOf(keyframes == null ? List.of() : keyframes);
        }

        public boolean isFixedWorldMode() {
            return "fixed_world".equalsIgnoreCase(mode);
        }

        public boolean isKeyframedMode() {
            return "keyframed".equalsIgnoreCase(mode);
        }

        public boolean isRelativePlayerMode() {
            return !isFixedWorldMode() && !isKeyframedMode();
        }
    }

    public record RelativePosition(double distance, double verticalOffset, double horizontalOffset, double forwardOffset) {
    }

    public record WorldPosition(String world, double x, double y, double z) {
    }

    public record Rotation(float yaw, float pitch, float roll) {
    }

    public record Keyframe(
        long timeMs,
        WorldPosition worldPosition,
        LookAt lookAt,
        Double fov,
        String easing
    ) {
        public Keyframe {
            easing = easing == null || easing.isBlank() ? "linear" : easing;
        }
    }

    public sealed interface LookAt permits WorldLookAt, EntityLookAt {
    }

    public record WorldLookAt(double x, double y, double z) implements LookAt {
    }

    public record EntityLookAt(String entityRef, double offsetX, double offsetY, double offsetZ) implements LookAt {
    }

    public record Movement(boolean allowPlayerMovement, boolean allowCameraMovement, boolean lockPlayerInput) {
    }

    public record Smoothing(boolean enabled, double positionSmoothTime, double rotationSmoothTime) {
    }

    public record Behavior(boolean resetOnDisable, boolean restorePlayerCameraOnDisable, boolean useVirtualEntityCamera) {
    }
}
