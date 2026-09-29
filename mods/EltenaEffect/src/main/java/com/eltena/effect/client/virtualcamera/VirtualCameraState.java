package com.eltena.effect.client.virtualcamera;

import java.util.List;
import java.util.Map;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class VirtualCameraState {
    private boolean enabled;
    private double cameraX;
    private double cameraY;
    private double cameraZ;
    private double targetCameraX;
    private double targetCameraY;
    private double targetCameraZ;
    private float cameraYaw;
    private float cameraPitch;
    private float cameraRoll;
    private float targetCameraYaw;
    private float targetCameraPitch;
    private float targetCameraRoll;
    private double cameraFov = Double.NaN;
    private double targetCameraFov = Double.NaN;
    private long lastUpdateNanos = -1L;

    boolean enabled() {
        return this.enabled;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            this.lastUpdateNanos = -1L;
            this.cameraFov = Double.NaN;
            this.targetCameraFov = Double.NaN;
        }
    }

    double cameraX() {
        return this.cameraX;
    }

    double cameraY() {
        return this.cameraY;
    }

    double cameraZ() {
        return this.cameraZ;
    }

    float cameraYaw() {
        return this.cameraYaw;
    }

    float cameraPitch() {
        return this.cameraPitch;
    }

    float cameraRoll() {
        return this.cameraRoll;
    }

    double cameraFov() {
        return this.cameraFov;
    }

    boolean hasCameraFov() {
        return !Double.isNaN(this.cameraFov);
    }

    void initializeFromPlayerView(LocalPlayer player, double partialTick) {
        Vec3 eyePosition = player.getEyePosition((float) partialTick);
        this.cameraX = eyePosition.x;
        this.cameraY = eyePosition.y;
        this.cameraZ = eyePosition.z;
        this.cameraYaw = Mth.rotLerp((float) partialTick, player.yRotO, player.getYRot());
        this.cameraPitch = Mth.lerp((float) partialTick, player.xRotO, player.getXRot());
        this.cameraRoll = 0.0F;
        this.targetCameraX = this.cameraX;
        this.targetCameraY = this.cameraY;
        this.targetCameraZ = this.cameraZ;
        this.targetCameraYaw = this.cameraYaw;
        this.targetCameraPitch = this.cameraPitch;
        this.targetCameraRoll = this.cameraRoll;
        this.cameraFov = Double.NaN;
        this.targetCameraFov = Double.NaN;
        this.lastUpdateNanos = -1L;
    }

    void prepareTarget(VirtualCameraConfig.Preset preset, LocalPlayer player, double partialTick) {
        if (preset.isFixedWorldMode()) {
            prepareFixedWorldTarget(preset);
            return;
        }

        prepareRelativePlayerTarget(preset, player, partialTick);
    }

    public void prepareKeyframedTarget(
        VirtualCameraConfig.Preset preset,
        long sceneStartTimeMs,
        Map<String, VirtualCameraConfig.WorldPosition> resolvedEntityLookAts
    ) {
        List<VirtualCameraConfig.Keyframe> keyframes = preset.keyframes();
        if (keyframes.isEmpty()) {
            return;
        }

        long elapsedMs = sceneStartTimeMs <= 0L ? 0L : Math.max(0L, System.currentTimeMillis() - sceneStartTimeMs);
        VirtualCameraConfig.Keyframe first = keyframes.get(0);
        VirtualCameraConfig.Keyframe last = keyframes.get(keyframes.size() - 1);

        if (elapsedMs <= first.timeMs()) {
            applyKeyframe(first, resolvedEntityLookAts);
            return;
        }
        if (elapsedMs >= last.timeMs()) {
            applyKeyframe(last, resolvedEntityLookAts);
            return;
        }

        VirtualCameraConfig.Keyframe previous = first;
        VirtualCameraConfig.Keyframe next = last;
        for (int index = 1; index < keyframes.size(); index++) {
            VirtualCameraConfig.Keyframe candidate = keyframes.get(index);
            if (elapsedMs <= candidate.timeMs()) {
                previous = keyframes.get(index - 1);
                next = candidate;
                break;
            }
        }

        long segmentDurationMs = Math.max(1L, next.timeMs() - previous.timeMs());
        double rawProgress = (elapsedMs - previous.timeMs()) / (double) segmentDurationMs;
        double progress = applyEasing(next.easing(), Mth.clamp(rawProgress, 0.0D, 1.0D));

        Vec3 cameraPosition = interpolateWorldPosition(previous.worldPosition(), next.worldPosition(), progress);
        this.targetCameraX = cameraPosition.x;
        this.targetCameraY = cameraPosition.y;
        this.targetCameraZ = cameraPosition.z;

        YawPitch yawPitch = interpolateLookAt(previous.lookAt(), next.lookAt(), cameraPosition, resolvedEntityLookAts, progress);
        if (yawPitch != null) {
            this.targetCameraYaw = yawPitch.yaw();
            this.targetCameraPitch = yawPitch.pitch();
        }
        this.targetCameraRoll = 0.0F;
        this.targetCameraFov = interpolateFov(previous.fov(), next.fov(), progress);
    }

    void tick(VirtualCameraConfig.Smoothing smoothing) {
        double deltaSeconds = nextDeltaSeconds();

        if (!smoothing.enabled() || smoothing.positionSmoothTime() <= 0.0D) {
            this.cameraX = this.targetCameraX;
            this.cameraY = this.targetCameraY;
            this.cameraZ = this.targetCameraZ;
        } else {
            double positionAlpha = smoothingAlpha(smoothing.positionSmoothTime(), deltaSeconds);
            this.cameraX = Mth.lerp(positionAlpha, this.cameraX, this.targetCameraX);
            this.cameraY = Mth.lerp(positionAlpha, this.cameraY, this.targetCameraY);
            this.cameraZ = Mth.lerp(positionAlpha, this.cameraZ, this.targetCameraZ);
        }

        if (!smoothing.enabled() || smoothing.rotationSmoothTime() <= 0.0D) {
            this.cameraYaw = this.targetCameraYaw;
            this.cameraPitch = this.targetCameraPitch;
            this.cameraRoll = this.targetCameraRoll;
            this.cameraFov = Double.isNaN(this.targetCameraFov) ? Double.NaN : this.targetCameraFov;
            return;
        }

        double rotationAlpha = smoothingAlpha(smoothing.rotationSmoothTime(), deltaSeconds);
        this.cameraYaw = Mth.rotLerp((float) rotationAlpha, this.cameraYaw, this.targetCameraYaw);
        this.cameraPitch = Mth.lerp((float) rotationAlpha, this.cameraPitch, this.targetCameraPitch);
        this.cameraRoll = Mth.lerp((float) rotationAlpha, this.cameraRoll, this.targetCameraRoll);
        this.cameraFov = Double.isNaN(this.targetCameraFov) ? Double.NaN : this.targetCameraFov;
    }

    void resetStoredState() {
        this.cameraX = this.targetCameraX;
        this.cameraY = this.targetCameraY;
        this.cameraZ = this.targetCameraZ;
        this.cameraYaw = this.targetCameraYaw;
        this.cameraPitch = this.targetCameraPitch;
        this.cameraRoll = this.targetCameraRoll;
        this.cameraFov = this.targetCameraFov;
        this.lastUpdateNanos = -1L;
    }

    private void prepareRelativePlayerTarget(VirtualCameraConfig.Preset preset, LocalPlayer player, double partialTick) {
        VirtualCameraConfig.RelativePosition relativePosition = preset.relativePosition();
        if (relativePosition == null) {
            return;
        }

        double playerX = Mth.lerp(partialTick, player.xo, player.getX());
        double playerY = Mth.lerp(partialTick, player.yo, player.getY());
        double playerZ = Mth.lerp(partialTick, player.zo, player.getZ());
        float yaw = Mth.rotLerp((float) partialTick, player.yRotO, player.getYRot());
        float pitch = Mth.lerp((float) partialTick, player.xRotO, player.getXRot());
        Vec3 horizontalForward = horizontalForward(yaw);
        Vec3 horizontalRight = new Vec3(horizontalForward.z, 0.0D, -horizontalForward.x);

        double baseAnchorY = playerY + Math.min(player.getBbHeight() * 0.55D, 1.0D);
        Vec3 origin = new Vec3(playerX, baseAnchorY, playerZ);
        Vec3 cameraPosition = origin
            .subtract(horizontalForward.scale(relativePosition.distance()))
            .add(horizontalRight.scale(relativePosition.horizontalOffset()))
            .add(horizontalForward.scale(relativePosition.forwardOffset()))
            .add(0.0D, relativePosition.verticalOffset(), 0.0D);

        this.targetCameraX = cameraPosition.x;
        this.targetCameraY = cameraPosition.y;
        this.targetCameraZ = cameraPosition.z;
        this.targetCameraYaw = yaw;
        this.targetCameraPitch = pitch;
        this.targetCameraRoll = 0.0F;
        this.targetCameraFov = Double.NaN;
    }

    private void prepareFixedWorldTarget(VirtualCameraConfig.Preset preset) {
        VirtualCameraConfig.WorldPosition worldPosition = preset.worldPosition();
        VirtualCameraConfig.Rotation rotation = preset.rotation();
        if (worldPosition == null || rotation == null) {
            return;
        }

        this.targetCameraX = worldPosition.x();
        this.targetCameraY = worldPosition.y();
        this.targetCameraZ = worldPosition.z();
        this.targetCameraYaw = rotation.yaw();
        this.targetCameraPitch = rotation.pitch();
        this.targetCameraRoll = rotation.roll();
        this.targetCameraFov = Double.NaN;
    }

    private double nextDeltaSeconds() {
        long now = System.nanoTime();
        if (this.lastUpdateNanos <= 0L) {
            this.lastUpdateNanos = now;
            return 0.0D;
        }

        double deltaSeconds = (now - this.lastUpdateNanos) / 1_000_000_000.0D;
        this.lastUpdateNanos = now;
        return Mth.clamp(deltaSeconds, 0.0D, 0.25D);
    }

    private static double smoothingAlpha(double smoothTime, double deltaSeconds) {
        if (smoothTime <= 0.0D) {
            return 1.0D;
        }
        if (deltaSeconds <= 0.0D) {
            return 0.0D;
        }

        return Mth.clamp(1.0D - Math.exp(-deltaSeconds / smoothTime), 0.0D, 1.0D);
    }

    private static Vec3 horizontalForward(float yawDegrees) {
        float yawRadians = yawDegrees * (float) (Math.PI / 180.0);
        double x = -Mth.sin(yawRadians);
        double z = Mth.cos(yawRadians);
        return new Vec3(x, 0.0D, z).normalize();
    }

    private void applyKeyframe(
        VirtualCameraConfig.Keyframe keyframe,
        Map<String, VirtualCameraConfig.WorldPosition> resolvedEntityLookAts
    ) {
        Vec3 currentTargetPosition = currentTargetPosition();
        Vec3 cameraPosition = resolveWorldPosition(keyframe.worldPosition());
        if (cameraPosition != null) {
            this.targetCameraX = cameraPosition.x;
            this.targetCameraY = cameraPosition.y;
            this.targetCameraZ = cameraPosition.z;
        } else {
            cameraPosition = currentTargetPosition;
        }

        YawPitch yawPitch = resolveYawPitch(keyframe.lookAt(), cameraPosition, resolvedEntityLookAts);
        if (yawPitch != null) {
            this.targetCameraYaw = yawPitch.yaw();
            this.targetCameraPitch = yawPitch.pitch();
        }
        this.targetCameraRoll = 0.0F;
        this.targetCameraFov = keyframe.fov() == null ? 70.0D : keyframe.fov();
    }

    private Vec3 currentTargetPosition() {
        return new Vec3(this.targetCameraX, this.targetCameraY, this.targetCameraZ);
    }

    private static Vec3 interpolateWorldPosition(
        VirtualCameraConfig.WorldPosition previous,
        VirtualCameraConfig.WorldPosition next,
        double progress
    ) {
        Vec3 previousPosition = resolveWorldPosition(previous);
        Vec3 nextPosition = resolveWorldPosition(next);
        if (previousPosition == null && nextPosition == null) {
            return new Vec3(0.0D, 0.0D, 0.0D);
        }
        if (previousPosition == null) {
            return nextPosition;
        }
        if (nextPosition == null) {
            return previousPosition;
        }
        return lerp(previousPosition, nextPosition, progress);
    }

    private static Vec3 resolveWorldPosition(VirtualCameraConfig.WorldPosition worldPosition) {
        if (worldPosition == null) {
            return null;
        }
        return new Vec3(worldPosition.x(), worldPosition.y(), worldPosition.z());
    }

    private static YawPitch interpolateLookAt(
        VirtualCameraConfig.LookAt previous,
        VirtualCameraConfig.LookAt next,
        Vec3 cameraPosition,
        Map<String, VirtualCameraConfig.WorldPosition> resolvedEntityLookAts,
        double progress
    ) {
        if (previous == null && next == null) {
            return null;
        }

        if (previous != null && next != null && previous.getClass() != next.getClass()) {
            YawPitch previousAngles = resolveYawPitch(previous, cameraPosition, resolvedEntityLookAts);
            YawPitch nextAngles = resolveYawPitch(next, cameraPosition, resolvedEntityLookAts);
            return interpolateYawPitch(previousAngles, nextAngles, progress);
        }

        Vec3 previousPoint = resolveLookAtPoint(previous, resolvedEntityLookAts);
        Vec3 nextPoint = resolveLookAtPoint(next, resolvedEntityLookAts);
        if (previousPoint == null && nextPoint == null) {
            return null;
        }

        Vec3 targetPoint = previousPoint == null
            ? nextPoint
            : nextPoint == null
                ? previousPoint
                : lerp(previousPoint, nextPoint, progress);
        return computeYawPitch(cameraPosition, targetPoint);
    }

    private static YawPitch resolveYawPitch(
        VirtualCameraConfig.LookAt lookAt,
        Vec3 cameraPosition,
        Map<String, VirtualCameraConfig.WorldPosition> resolvedEntityLookAts
    ) {
        Vec3 targetPoint = resolveLookAtPoint(lookAt, resolvedEntityLookAts);
        return targetPoint == null ? null : computeYawPitch(cameraPosition, targetPoint);
    }

    private static Vec3 resolveLookAtPoint(
        VirtualCameraConfig.LookAt lookAt,
        Map<String, VirtualCameraConfig.WorldPosition> resolvedEntityLookAts
    ) {
        if (lookAt == null) {
            return null;
        }
        if (lookAt instanceof VirtualCameraConfig.WorldLookAt worldLookAt) {
            return new Vec3(worldLookAt.x(), worldLookAt.y(), worldLookAt.z());
        }
        if (lookAt instanceof VirtualCameraConfig.EntityLookAt entityLookAt) {
            VirtualCameraConfig.WorldPosition resolvedTarget = resolvedEntityLookAts.get(entityLookAt.entityRef());
            if (resolvedTarget == null) {
                return null;
            }
            return new Vec3(
                resolvedTarget.x() + entityLookAt.offsetX(),
                resolvedTarget.y() + entityLookAt.offsetY(),
                resolvedTarget.z() + entityLookAt.offsetZ()
            );
        }
        throw new IllegalArgumentException("Unsupported lookAt type: " + lookAt.getClass().getName());
    }

    private static YawPitch computeYawPitch(Vec3 cameraPosition, Vec3 targetPoint) {
        Vec3 delta = targetPoint.subtract(cameraPosition);
        double horizontalDistance = Math.sqrt((delta.x * delta.x) + (delta.z * delta.z));
        if (horizontalDistance < 1.0E-6D && Math.abs(delta.y) < 1.0E-6D) {
            return null;
        }

        float yaw = (float) Math.toDegrees(Mth.atan2(-delta.x, delta.z));
        float pitch = (float) Math.toDegrees(Mth.atan2(-delta.y, horizontalDistance));
        return new YawPitch(yaw, pitch);
    }

    private static YawPitch interpolateYawPitch(YawPitch previous, YawPitch next, double progress) {
        if (previous == null) {
            return next;
        }
        if (next == null) {
            return previous;
        }
        return new YawPitch(
            Mth.rotLerp((float) progress, previous.yaw(), next.yaw()),
            Mth.lerp((float) progress, previous.pitch(), next.pitch())
        );
    }

    private static Vec3 lerp(Vec3 start, Vec3 end, double progress) {
        return new Vec3(
            Mth.lerp(progress, start.x, end.x),
            Mth.lerp(progress, start.y, end.y),
            Mth.lerp(progress, start.z, end.z)
        );
    }

    private static double interpolateFov(Double previous, Double next, double progress) {
        double previousValue = previous == null ? 70.0D : previous;
        double nextValue = next == null ? 70.0D : next;
        return Mth.lerp(progress, previousValue, nextValue);
    }

    private static double applyEasing(String easing, double progress) {
        return switch (easing == null ? "linear" : easing.toLowerCase()) {
            case "ease-in" -> progress * progress;
            case "ease-out" -> 1.0D - ((1.0D - progress) * (1.0D - progress));
            case "ease-in-out" -> progress < 0.5D
                ? 2.0D * progress * progress
                : 1.0D - (2.0D * (1.0D - progress) * (1.0D - progress));
            case "catmull-rom" -> {
                // TODO: Replace with real Catmull-Rom spline evaluation once control points are available client-side.
                yield progress < 0.5D
                    ? 2.0D * progress * progress
                    : 1.0D - (2.0D * (1.0D - progress) * (1.0D - progress));
            }
            default -> progress;
        };
    }

    private record YawPitch(float yaw, float pitch) {
    }
}
