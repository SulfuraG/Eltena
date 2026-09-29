package com.eltena.effect.effect;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.slf4j.Logger;

public final class EarthquakeEffectManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEBUG_PROPERTY = "eltenaeffect.debug";
    private static final String LEGACY_DEBUG_PROPERTY = "eltena.debug";
    private static final float MAX_CAMERA_OFFSET = 4.0F;
    private static final float MAX_FOV_OFFSET = 7.5F;
    private static final List<ActiveEarthquake> ACTIVE = new ArrayList<>();

    private EarthquakeEffectManager() {
    }

    public static void register(IEventBus eventBus) {
        eventBus.addListener(EarthquakeEffectManager::onClientTick);
        eventBus.addListener(EarthquakeEffectManager::onComputeCameraAngles);
        eventBus.addListener(EarthquakeEffectManager::onComputeFov);
    }

    public static void enqueue(EarthquakePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            if (isDebugEnabled()) {
                LOGGER.info(translate("log.eltenaeffect.skip_no_player"));
            }
            return;
        }

        double playerX = minecraft.player.getX();
        double playerY = minecraft.player.getY();
        double playerZ = minecraft.player.getZ();
        double dx = payload.source().x() - playerX;
        double dy = payload.source().y() - playerY;
        double dz = payload.source().z() - playerZ;
        double distance = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
        double radius = Math.max(0.01D, payload.radius());

        if (distance > radius) {
            if (isDebugEnabled()) {
                LOGGER.info(translate("log.eltenaeffect.skip_out_of_range", payload.effectId()));
            }
            return;
        }

        if (isDebugEnabled()) {
            String currentDimension = minecraft.player.level().dimension().location().toString();
            LOGGER.info(translate("log.eltenaeffect.world_hint", payload.source().world(), currentDimension));
        }

        long delayMs = computeDelayMs(payload, distance);
        double strength = computeStrength(payload, distance, radius);
        if (strength <= 0.0D) {
            return;
        }

        ACTIVE.add(
            new ActiveEarthquake(
                payload,
                Util.getMillis(),
                delayMs,
                strength,
                computeSeed(payload)
            )
        );
        if (isDebugEnabled()) {
            LOGGER.info(translate("log.eltenaeffect.started", payload.effectId()));
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            ACTIVE.clear();
            return;
        }

        long now = Util.getMillis();
        Iterator<ActiveEarthquake> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().isExpired(now)) {
                iterator.remove();
            }
        }
    }

    private static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (ACTIVE.isEmpty()) {
            return;
        }

        CameraOffsets offsets = accumulateOffsets(Util.getMillis());

        if (offsets.isZero()) {
            return;
        }

        event.setYaw(event.getYaw() + clamp((float) offsets.yaw()));
        event.setPitch(event.getPitch() + clamp((float) offsets.pitch()));
        event.setRoll(event.getRoll() + clamp((float) offsets.roll()));
    }

    private static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (ACTIVE.isEmpty()) {
            return;
        }

        CameraOffsets offsets = accumulateOffsets(Util.getMillis());
        if (offsets.fov() == 0.0D) {
            return;
        }

        event.setFOV(event.getFOV() + clampFov((float) offsets.fov()));
    }

    private static long computeDelayMs(EarthquakePayload payload, double distance) {
        if (!payload.distanceDelay()) {
            return 0L;
        }
        double effectiveDistance = Math.max(0.0D, distance - Math.max(0.0D, payload.nearInstantRadius()));
        if (effectiveDistance <= 0.0D) {
            return 0L;
        }
        return Math.round((effectiveDistance / Math.max(1.0D, payload.waveSpeed())) * 1000.0D);
    }

    private static double computeStrength(EarthquakePayload payload, double distance, double radius) {
        double normalizedDistance = clamp(distance / Math.max(0.01D, radius), 0.0D, 1.0D);
        double falloffFactor = switch ((payload.distanceFalloff().type() == null
            ? "LINEAR"
            : payload.distanceFalloff().type()).toUpperCase(Locale.ROOT)) {
            case "LINEAR" -> 1.0D - normalizedDistance;
            default -> 1.0D - normalizedDistance;
        };

        double baseStrength = Math.max(0.0D, payload.basePower());
        double minimumStrength = clamp(payload.distanceFalloff().minimumStrength(), 0.0D, baseStrength);
        double computedStrength = Math.max(minimumStrength, baseStrength * falloffFactor);
        return clamp(computedStrength, 0.0D, baseStrength);
    }

    private static double computeSeed(EarthquakePayload payload) {
        long hash = 17L;
        hash = (31L * hash) + payload.effectId().hashCode();
        hash = (31L * hash) + payload.shakePattern().name().hashCode();
        hash = (31L * hash) + Double.doubleToLongBits(payload.source().x());
        hash = (31L * hash) + Double.doubleToLongBits(payload.source().z());
        return (hash & 0xFFFFL) / 173.0D;
    }

    private static CameraOffsets accumulateOffsets(long now) {
        double yawOffset = 0.0D;
        double pitchOffset = 0.0D;
        double rollOffset = 0.0D;
        double fovOffset = 0.0D;

        for (ActiveEarthquake earthquake : ACTIVE) {
            double intensity = earthquake.intensity(now);
            if (intensity <= 0.0D) {
                continue;
            }

            PatternSample sample = samplePattern(earthquake, now);
            yawOffset += intensity * sample.yaw();
            pitchOffset += intensity * sample.pitch();
            rollOffset += intensity * sample.roll();
            fovOffset += intensity * sample.fov();
        }

        return new CameraOffsets(yawOffset, pitchOffset, rollOffset, fovOffset);
    }

    private static PatternSample samplePattern(ActiveEarthquake earthquake, long now) {
        double seconds = earthquake.elapsedSeconds(now);
        double progress = earthquake.progress(now);
        double seed = earthquake.seed();
        long tickIndex = earthquake.elapsedTicks(now);

        return switch (earthquake.payload().shakePattern()) {
            case JITTER -> sampleJitter(seconds, tickIndex, seed);
            case HEAVY_RUMBLE -> sampleHeavyRumble(seconds, progress, seed);
            case SHOCKWAVE -> sampleShockwave(seconds, progress, seed);
            case PULSE -> samplePulse(seconds, progress, seed);
            case SWAY -> sampleSway(seconds, seed);
        };
    }

    private static PatternSample sampleSway(double seconds, double seed) {
        double yaw = (Math.sin((seconds * 4.6D) + (seed * 0.40D)) * 1.05D)
            + (Math.sin((seconds * 2.1D) + (seed * 0.15D)) * 0.35D);
        double pitch = (Math.cos((seconds * 3.8D) + (seed * 0.85D)) * 0.42D)
            + (Math.sin((seconds * 1.7D) + (seed * 0.45D)) * 0.12D);
        double roll = (Math.sin((seconds * 3.2D) + (seed * 0.55D)) * 0.18D);
        return new PatternSample(yaw, pitch, roll, 0.0D);
    }

    private static PatternSample sampleJitter(double seconds, long tickIndex, double seed) {
        double yaw = (stepNoise(seed, tickIndex, 1L) * 0.90D)
            + (stepNoise(seed, tickIndex, 2L) * 0.35D);
        double pitch = (stepNoise(seed, tickIndex, 3L) * 1.15D)
            + (stepNoise(seed, tickIndex, 4L) * 0.25D);
        double roll = (stepNoise(seed, tickIndex, 5L) * 1.35D)
            + (Math.signum(stepNoise(seed, tickIndex, 6L)) * 0.45D);
        double fov = stepNoise(seed, tickIndex, 7L) * 0.18D;
        return new PatternSample(yaw, pitch, roll, fov);
    }

    private static PatternSample sampleHeavyRumble(double seconds, double progress, double seed) {
        double thump = Math.pow(
            Math.max(0.0D, Math.sin((seconds * 3.0D) + (seed * 0.10D))),
            2.6D
        );
        double undercurrent = Math.sin((seconds * 1.35D) + (seed * 0.25D));
        double resonance = Math.sin((seconds * 2.15D) + (seed * 0.45D));
        double yaw = (undercurrent * 0.10D) + (resonance * 0.06D);
        double pitch = (-0.18D * undercurrent) + (thump * 0.24D);
        double roll = (Math.sin((seconds * 1.8D) + (seed * 0.35D)) * 0.14D) + (thump * 1.45D);
        double fov = (thump * 0.55D) - (Math.max(0.0D, progress - 0.15D) * 0.08D);
        return new PatternSample(yaw, pitch, roll, fov);
    }

    private static PatternSample sampleShockwave(double seconds, double progress, double seed) {
        double impulse = Math.exp(-Math.max(0.0D, progress) * 17.0D);
        double aftershockEnvelope = Math.exp(-Math.max(0.0D, progress) * 4.4D);
        double aftershock = (
            (Math.sin((seconds * 14.0D) + (seed * 0.65D)) * 0.65D)
                + (Math.cos((seconds * 25.0D) + (seed * 0.25D)) * 0.35D)
        ) * aftershockEnvelope;
        double yaw = (Math.copySign(1.40D, Math.sin(seed * 0.7D)) * impulse) + (aftershock * 0.30D);
        double pitch = (-1.95D * impulse) + (aftershock * 0.20D);
        double roll = (Math.copySign(2.25D, Math.cos(seed * 0.35D)) * impulse) + (aftershock * 0.55D);
        double fov = (5.25D * impulse) - (aftershockEnvelope * 0.35D);
        return new PatternSample(yaw, pitch, roll, fov);
    }

    private static PatternSample samplePulse(double seconds, double progress, double seed) {
        double intervalSeconds = 1.05D;
        double phase = normalizedPhase(seconds + (seed * 0.03D), intervalSeconds);
        double pulse = gaussianPulse(phase, 0.0D, 0.085D)
            + gaussianPulse(phase, 1.0D, 0.085D);
        double tail = gaussianPulse(phase, 0.18D, 0.14D);
        double undertone = Math.sin((seconds * 1.6D) + (seed * 0.20D)) * 0.08D;
        double kickDirection = Math.copySign(1.0D, Math.sin(seed * 0.9D));
        double yaw = undertone + (kickDirection * pulse * 0.38D);
        double pitch = (pulse * 1.10D) + (tail * 0.20D);
        double roll = (kickDirection * pulse * 1.65D) + (Math.cos((seconds * 7.0D) + seed) * tail * 0.22D);
        double fov = (pulse * 1.35D) + (tail * 0.18D) - (progress * 0.06D);
        return new PatternSample(yaw, pitch, roll, fov);
    }

    private static double normalizedPhase(double seconds, double intervalSeconds) {
        double phase = (seconds / Math.max(0.05D, intervalSeconds)) % 1.0D;
        return phase < 0.0D ? phase + 1.0D : phase;
    }

    private static double gaussianPulse(double value, double center, double sigma) {
        double distance = value - center;
        return Math.exp(-((distance * distance) / Math.max(0.0001D, 2.0D * sigma * sigma)));
    }

    private static double stepNoise(double seed, long tickIndex, long salt) {
        long mixed = Double.doubleToLongBits(seed);
        mixed ^= (tickIndex * 0x9E3779B97F4A7C15L);
        mixed ^= (salt * 0xBF58476D1CE4E5B9L);
        mixed ^= (mixed >>> 30);
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= (mixed >>> 27);
        mixed *= 0x94D049BB133111EBL;
        mixed ^= (mixed >>> 31);
        return (((mixed & 0xFFFFL) / 65535.0D) * 2.0D) - 1.0D;
    }

    private static float clamp(float value) {
        return Math.max(-MAX_CAMERA_OFFSET, Math.min(MAX_CAMERA_OFFSET, value));
    }

    private static float clampFov(float value) {
        return Math.max(-MAX_FOV_OFFSET, Math.min(MAX_FOV_OFFSET, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isDebugEnabled() {
        return Boolean.getBoolean(DEBUG_PROPERTY) || Boolean.getBoolean(LEGACY_DEBUG_PROPERTY);
    }

    private static String translate(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private record ActiveEarthquake(
        EarthquakePayload payload,
        long startedAtMs,
        long delayMs,
        double resolvedStrength,
        double seed
    ) {
        boolean isExpired(long now) {
            return now >= startedAtMs + delayMs + Math.max(1L, payload.durationMs());
        }

        double elapsedSeconds(long now) {
            return Math.max(0.0D, (now - startedAtMs - delayMs) / 1000.0D);
        }

        long elapsedTicks(long now) {
            long elapsed = now - startedAtMs - delayMs;
            return Math.max(0L, elapsed / 50L);
        }

        double progress(long now) {
            long elapsed = now - startedAtMs - delayMs;
            long duration = Math.max(1L, payload.durationMs());
            return clamp(elapsed / (double) duration, 0.0D, 1.0D);
        }

        double intensity(long now) {
            long elapsed = now - startedAtMs - delayMs;
            long duration = Math.max(1L, payload.durationMs());
            if (elapsed < 0L || elapsed >= duration) {
                return 0.0D;
            }
            return resolvedStrength * envelope(elapsed, duration);
        }

        private double envelope(long elapsed, long duration) {
            long rise = Math.max(0L, payload.riseMs());
            long fall = Math.max(0L, payload.fallMs());
            long fallStart = Math.max(rise, duration - fall);

            if (rise > 0L && elapsed < rise) {
                double progress = elapsed / (double) rise;
                return Math.sin(progress * Math.PI * 0.5D);
            }
            if (fall > 0L && elapsed >= fallStart) {
                double progress = (elapsed - fallStart) / (double) fall;
                double clamped = Math.max(0.0D, Math.min(1.0D, progress));
                return Math.cos(clamped * Math.PI * 0.5D);
            }
            return 1.0D;
        }
    }

    private record PatternSample(double yaw, double pitch, double roll, double fov) {
    }

    private record CameraOffsets(double yaw, double pitch, double roll, double fov) {
        boolean isZero() {
            return yaw == 0.0D && pitch == 0.0D && roll == 0.0D;
        }
    }
}
