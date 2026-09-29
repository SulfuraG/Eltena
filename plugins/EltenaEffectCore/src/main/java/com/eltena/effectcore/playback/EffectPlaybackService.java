package com.eltena.effectcore.playback;

import com.eltena.effectcore.EltenaEffectCorePlugin;
import com.eltena.effectcore.config.EffectRegistry;
import com.eltena.effectcore.effect.DistanceFalloffDefinition;
import com.eltena.effectcore.effect.DistanceFalloffType;
import com.eltena.effectcore.effect.EarthquakeEffectDefinition;
import com.eltena.effectcore.effect.EffectDefinition;
import com.eltena.effectcore.effect.EffectScope;
import com.eltena.effectcore.effect.RepeatSettings;
import com.eltena.effectcore.effect.ScreenColorDefinition;
import com.eltena.effectcore.effect.ScreenEffectDefinition;
import com.eltena.effectcore.effect.ScreenShaderDefinition;
import com.eltena.effectcore.transport.EffectPluginMessenger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class EffectPlaybackService {
    private final EltenaEffectCorePlugin plugin;
    private final EffectRegistry effectRegistry;
    private final EffectPluginMessenger messenger;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public EffectPlaybackService(
        EltenaEffectCorePlugin plugin,
        EffectRegistry effectRegistry,
        EffectPluginMessenger messenger
    ) {
        this.plugin = plugin;
        this.effectRegistry = effectRegistry;
        this.messenger = messenger;
    }

    public PlayResult play(String effectId, Location sourceLocation) {
        return play(effectId, sourceLocation, null);
    }

    public PlayResult play(String effectId, Location sourceLocation, Long screenDurationOverrideMs) {
        if (sourceLocation == null || sourceLocation.getWorld() == null) {
            throw new IllegalArgumentException("Source location must include a world.");
        }

        EffectDefinition effect = effectRegistry.require(effectId);
        int targetCount;
        int scheduledRepeatCount;
        if (effect instanceof EarthquakeEffectDefinition earthquakeEffect) {
            targetCount = dispatchEarthquake(earthquakeEffect, sourceLocation, 1.0D);
            scheduledRepeatCount = scheduleRepeats(earthquakeEffect, sourceLocation);
        } else if (effect instanceof ScreenEffectDefinition screenEffect) {
            targetCount = dispatchScreenEffect(
                screenEffect,
                sourceLocation,
                screenDurationOverrideMs
            );
            scheduledRepeatCount = 0;
        } else {
            throw new IllegalStateException("Unsupported effect definition: " + effect.getClass().getName());
        }

        if (targetCount == 0 && plugin.isDebugLogEnabled()) {
            plugin.logInfo(
                "log.no-targets",
                Map.of(
                    "effectId", effect.id(),
                    "world", sourceLocation.getWorld().getName(),
                    "x", plugin.formatDecimal(sourceLocation.getX()),
                    "y", plugin.formatDecimal(sourceLocation.getY()),
                    "z", plugin.formatDecimal(sourceLocation.getZ())
                )
            );
        }

        return new PlayResult(effect.id(), effect.displayName(), targetCount, scheduledRepeatCount);
    }

    private int scheduleRepeats(EarthquakeEffectDefinition effect, Location sourceLocation) {
        RepeatSettings repeat = effect.repeat();
        if (!repeat.enabled()) {
            return 0;
        }

        long intervalTicks = Math.max(1L, Math.round(repeat.intervalMs() / 50.0D));
        for (int repeatIndex = 1; repeatIndex <= repeat.count(); repeatIndex++) {
            final int scheduledIndex = repeatIndex;
            final Location scheduledSource = sourceLocation.clone();
            final double powerScale = Math.pow(repeat.powerMultiplier(), repeatIndex);
            plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> dispatchEarthquake(effect, scheduledSource, powerScale),
                intervalTicks * repeatIndex
            );
            if (plugin.isDebugLogEnabled()) {
                plugin.logInfo(
                    "log.scheduled-repeat",
                    Map.of(
                        "effectId", effect.id(),
                        "repeatIndex", Integer.toString(scheduledIndex),
                        "delayTicks", Long.toString(intervalTicks * repeatIndex)
                    )
                );
            }
        }
        return repeat.count();
    }

    private int dispatchEarthquake(
        EarthquakeEffectDefinition effect,
        Location sourceLocation,
        double powerScale
    ) {
        List<Player> targets = selectTargets(effect.scope(), sourceLocation, effect.radius());
        double adjustedBasePower = Math.max(0.0D, effect.basePower() * powerScale);
        for (Player player : targets) {
            JsonObject payload = buildEarthquakePayload(effect, sourceLocation, adjustedBasePower);
            messenger.send(
                player,
                effect.id(),
                adjustedBasePower,
                gson.toJson(payload).getBytes(StandardCharsets.UTF_8)
            );
        }
        return targets.size();
    }

    private int dispatchScreenEffect(
        ScreenEffectDefinition effect,
        Location sourceLocation,
        Long durationOverrideMs
    ) {
        long resolvedDurationMs = resolveScreenDurationMs(effect.durationMs(), durationOverrideMs);
        List<Player> targets = selectWorldTargets(sourceLocation);
        for (Player player : targets) {
            JsonObject payload = buildScreenEffectPayload(effect, resolvedDurationMs);
            messenger.send(
                player,
                effect.id(),
                effect.strength(),
                gson.toJson(payload).getBytes(StandardCharsets.UTF_8)
            );
        }
        return targets.size();
    }

    private long resolveScreenDurationMs(long defaultDurationMs, Long durationOverrideMs) {
        if (durationOverrideMs == null || durationOverrideMs <= 0L) {
            return defaultDurationMs;
        }
        return durationOverrideMs;
    }

    private List<Player> selectTargets(
        EffectScope scope,
        Location sourceLocation,
        double radius
    ) {
        World world = sourceLocation.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Source world is required.");
        }
        double radiusSquared = radius * radius;
        return switch (scope) {
            case WORLD_RADIUS -> world.getPlayers()
                .stream()
                .filter(player -> player.getLocation().distanceSquared(sourceLocation) <= radiusSquared)
                .toList();
        };
    }

    private List<Player> selectWorldTargets(Location sourceLocation) {
        World world = sourceLocation.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Source world is required.");
        }
        return world.getPlayers()
            .stream()
            .filter(Player::isOnline)
            .toList();
    }

    private JsonObject buildEarthquakePayload(
        EarthquakeEffectDefinition effect,
        Location sourceLocation,
        double adjustedBasePower
    ) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "earthquake");
        payload.addProperty("effectId", effect.id());
        payload.addProperty("shakePattern", effect.shakePattern().name());

        JsonObject source = new JsonObject();
        source.addProperty("world", sourceLocation.getWorld().getName());
        source.addProperty("x", sourceLocation.getX());
        source.addProperty("y", sourceLocation.getY());
        source.addProperty("z", sourceLocation.getZ());
        payload.add("source", source);

        payload.addProperty("scope", effect.scope().name());
        payload.addProperty("radius", effect.radius());
        payload.addProperty("basePower", adjustedBasePower);
        payload.addProperty("waveSpeed", effect.waveSpeed());
        payload.addProperty("durationMs", effect.durationMs());
        payload.addProperty("riseMs", effect.riseMs());
        payload.addProperty("fallMs", effect.fallMs());
        payload.addProperty("nearInstantRadius", effect.nearInstantRadius());
        payload.addProperty("distanceDelay", effect.distanceDelay());
        payload.add("distanceFalloff", buildDistanceFalloff(effect.distanceFalloff()));
        return payload;
    }

    private JsonObject buildDistanceFalloff(DistanceFalloffDefinition distanceFalloff) {
        JsonObject payload = new JsonObject();
        DistanceFalloffType type = distanceFalloff.type() == null
            ? DistanceFalloffType.LINEAR
            : distanceFalloff.type();
        payload.addProperty("type", type.name());
        payload.addProperty("minimumStrength", distanceFalloff.minimumStrength());
        return payload;
    }

    private JsonObject buildScreenEffectPayload(ScreenEffectDefinition effect, long durationMs) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "screen_effect");
        payload.addProperty("effectId", effect.id());
        payload.addProperty("visualPattern", effect.visualPattern().name());
        payload.addProperty("durationMs", durationMs);
        payload.addProperty("strength", effect.strength());
        payload.addProperty("fadeInMs", effect.fadeInMs());
        payload.addProperty("fadeOutMs", effect.fadeOutMs());
        payload.addProperty("debugLog", plugin.isDebugLogEnabled());
        payload.add("color", buildScreenColor(effect.color()));
        if (effect.shader() != null && effect.shader().enabled()) {
            payload.add("shader", buildScreenShader(effect.shader()));
        }
        return payload;
    }

    private JsonObject buildScreenColor(ScreenColorDefinition color) {
        JsonObject payload = new JsonObject();
        payload.addProperty("r", color.r());
        payload.addProperty("g", color.g());
        payload.addProperty("b", color.b());
        payload.addProperty("a", color.a());
        return payload;
    }

    private JsonObject buildScreenShader(ScreenShaderDefinition shader) {
        JsonObject payload = new JsonObject();
        payload.addProperty("enabled", shader.enabled());
        if (shader.legacyActive()) {
            payload.addProperty("type", shader.type().name());
            payload.addProperty("strength", shader.strength());
            payload.addProperty("samples", shader.samples());
            payload.addProperty("radius", shader.radius());
            payload.addProperty("chromaticOffset", shader.chromaticOffset());
            payload.addProperty("pressureDistortion", shader.pressureDistortion());
        }
        if (!shader.layers().isEmpty()) {
            JsonArray layers = new JsonArray();
            for (ScreenShaderDefinition.Layer layer : shader.layers()) {
                layers.add(buildScreenShaderLayer(layer));
            }
            payload.add("layers", layers);
        }
        return payload;
    }

    private JsonObject buildScreenShaderLayer(ScreenShaderDefinition.Layer layer) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", layer.type().name());
        payload.addProperty("strength", layer.strength());
        payload.addProperty("samples", layer.samples());
        payload.addProperty("radius", layer.radius());
        payload.addProperty("frequency", layer.frequency());
        payload.addProperty("speed", layer.speed());
        payload.addProperty("edgeOnly", layer.edgeOnly());
        payload.addProperty("direction", layer.direction().name());
        payload.addProperty("startProgress", layer.startProgress());
        payload.addProperty("endProgress", layer.endProgress());
        payload.addProperty("curve", layer.curve().name());
        payload.add("color", buildShaderLayerColor(layer.color()));
        payload.addProperty("noiseStrength", layer.noiseStrength());
        payload.addProperty("wobble", layer.wobble());
        payload.addProperty("blurScale", layer.blurScale());
        payload.addProperty("refractionStrength", layer.refractionStrength());
        payload.addProperty("desaturation", layer.desaturation());
        payload.addProperty("smear", layer.smear());
        payload.addProperty("pulse", layer.pulse());
        payload.addProperty("count", layer.count());
        payload.addProperty("length", layer.length());
        payload.addProperty("sharpness", layer.sharpness());
        payload.addProperty("centerBias", layer.centerBias());
        payload.addProperty("edgeBias", layer.edgeBias());
        payload.addProperty("chromaticOffset", layer.chromaticOffset());
        return payload;
    }

    private JsonObject buildShaderLayerColor(ScreenShaderDefinition.Color color) {
        JsonObject payload = new JsonObject();
        payload.addProperty("r", color.r());
        payload.addProperty("g", color.g());
        payload.addProperty("b", color.b());
        payload.addProperty("a", color.a());
        return payload;
    }
}
