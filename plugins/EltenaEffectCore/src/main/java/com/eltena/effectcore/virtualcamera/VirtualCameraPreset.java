package com.eltena.effectcore.virtualcamera;

import java.util.List;
import org.bukkit.util.Vector;

public record VirtualCameraPreset(
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
    public VirtualCameraPreset {
        keyframes = keyframes == null ? List.of() : List.copyOf(keyframes);
    }

    public boolean isFixedWorldMode() {
        return "fixed_world".equalsIgnoreCase(mode);
    }

    public boolean isRelativePlayerMode() {
        return "relative_player".equalsIgnoreCase(mode);
    }

    public boolean isKeyframedMode() {
        return "keyframed".equalsIgnoreCase(mode);
    }

    public long totalDurationMs() {
        if (keyframes.isEmpty()) {
            return 0L;
        }
        return keyframes.get(keyframes.size() - 1).timeMs();
    }

    public record RelativePosition(
        double distance,
        double verticalOffset,
        double horizontalOffset,
        double forwardOffset
    ) {
    }

    public record WorldPosition(
        String world,
        double x,
        double y,
        double z
    ) {
    }

    public record Rotation(
        float yaw,
        float pitch,
        float roll
    ) {
    }

    public record Movement(boolean allowPlayerMovement, boolean allowCameraMovement, boolean lockPlayerInput) {
    }

    public record Smoothing(boolean enabled, double positionSmoothTime, double rotationSmoothTime) {
    }

    public record Behavior(boolean resetOnDisable, boolean restorePlayerCameraOnDisable, boolean useVirtualEntityCamera) {
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

    public sealed interface LookAt permits EntityLookAt, WorldLookAt {
    }

    public record EntityLookAt(String entityRef, Vector offset) implements LookAt {
        public EntityLookAt {
            offset = offset == null ? new Vector(0.0D, 0.0D, 0.0D) : offset.clone();
        }
    }

    public record WorldLookAt(WorldPosition position) implements LookAt {
    }
}
