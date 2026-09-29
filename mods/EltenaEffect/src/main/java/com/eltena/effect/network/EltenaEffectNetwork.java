package com.eltena.effect.network;

import com.eltena.effect.client.virtualcamera.VirtualCameraConfig;
import com.eltena.effect.client.virtualcamera.VirtualCameraController;
import com.eltena.effect.effect.EarthquakeEffectManager;
import com.eltena.effect.effect.EarthquakePayload;
import com.eltena.effect.effect.EarthquakeShakePattern;
import com.eltena.effect.effect.ScreenEffectManager;
import com.eltena.effect.effect.ScreenEffectPayload;
import com.eltena.effect.effect.ScreenShaderCurve;
import com.eltena.effect.effect.ScreenShaderDirection;
import com.eltena.effect.effect.ScreenShaderType;
import com.eltena.effect.effect.ScreenVisualPattern;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

public final class EltenaEffectNetwork {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEBUG_PROPERTY = "eltenaeffect.debug";
    private static final String LEGACY_DEBUG_PROPERTY = "eltena.debug";

    private EltenaEffectNetwork() {
    }

    public static void bootstrap(IEventBus modBus) {
        modBus.addListener(EltenaEffectNetwork::onRegisterPayloadHandlers);
    }

    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToClient(EffectS2CPayload.TYPE, EffectS2CPayload.STREAM_CODEC, (payload, context) -> {
            try {
                if (isDebugEnabled()) {
                    LOGGER.info(
                        translate(
                            "log.eltenaeffect.transport",
                            Integer.toString(payload.byteLength()),
                            Integer.toString(payload.jsonByteLength())
                        )
                    );
                }
                handleIncomingEffect(payload.json());
            } catch (RuntimeException exception) {
                String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
                LOGGER.error(translate("log.eltenaeffect.decode_error", message), exception);
            }
        });
    }

    private static void handleIncomingEffect(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return;
        }

        JsonObject root = JsonParser.parseString(rawJson).getAsJsonObject();
        String type = readString(root, "type", "");
        if ("earthquake".equalsIgnoreCase(type)) {
            EarthquakePayload payload = parseEarthquake(root);
            if (isDebugEnabled()) {
                LOGGER.info(translate("log.eltenaeffect.received", payload.effectId()));
            }
            EarthquakeEffectManager.enqueue(payload);
            return;
        }
        if ("screen_effect".equalsIgnoreCase(type)) {
            ScreenEffectPayload payload = parseScreenEffect(root);
            if (isDebugEnabled()) {
                LOGGER.info(translate("log.eltenaeffect.received", payload.effectId()));
            }
            ScreenEffectManager.enqueue(payload);
            return;
        }
        if ("virtual_camera_config".equalsIgnoreCase(type)) {
            VirtualCameraController.applyServerConfig(parseVirtualCameraConfig(root));
            if (isDebugEnabled()) {
                LOGGER.info(translate("log.eltenaeffect.received", "virtual_camera_config"));
            }
            return;
        }

        LOGGER.warn(
            translate(
                "log.eltenaeffect.unknown_type",
                type.isBlank() ? "<blank>" : type
            )
        );
    }

    private static EarthquakePayload parseEarthquake(JsonObject root) {
        JsonObject sourceObject = root.has("source") && root.get("source").isJsonObject()
            ? root.getAsJsonObject("source")
            : new JsonObject();
        JsonObject falloffObject = root.has("distanceFalloff") && root.get("distanceFalloff").isJsonObject()
            ? root.getAsJsonObject("distanceFalloff")
            : new JsonObject();

        return new EarthquakePayload(
            readString(root, "effectId", "unknown"),
            EarthquakeShakePattern.fromPayload(readString(root, "shakePattern", "SWAY")),
            new EarthquakePayload.Source(
                readString(sourceObject, "world", ""),
                readDouble(sourceObject, "x", 0.0D),
                readDouble(sourceObject, "y", 0.0D),
                readDouble(sourceObject, "z", 0.0D)
            ),
            readString(root, "scope", "WORLD_RADIUS"),
            Math.max(0.01D, readDouble(root, "radius", 0.01D)),
            Math.max(0.0D, readDouble(root, "basePower", 0.0D)),
            Math.max(1.0D, readDouble(root, "waveSpeed", 1.0D)),
            Math.max(1L, readLong(root, "durationMs", 1L)),
            Math.max(0L, readLong(root, "riseMs", 0L)),
            Math.max(0L, readLong(root, "fallMs", 0L)),
            Math.max(0.0D, readDouble(root, "nearInstantRadius", 0.0D)),
            readBoolean(root, "distanceDelay", true),
            new EarthquakePayload.DistanceFalloff(
                readString(falloffObject, "type", "LINEAR"),
                Math.max(0.0D, readDouble(falloffObject, "minimumStrength", 0.0D))
            )
        );
    }

    private static ScreenEffectPayload parseScreenEffect(JsonObject root) {
        JsonObject colorObject = root.has("color") && root.get("color").isJsonObject()
            ? root.getAsJsonObject("color")
            : new JsonObject();
        JsonObject shaderObject = root.has("shader") && root.get("shader").isJsonObject()
            ? root.getAsJsonObject("shader")
            : new JsonObject();

        return new ScreenEffectPayload(
            readString(root, "effectId", "unknown"),
            ScreenVisualPattern.fromPayload(readString(root, "visualPattern", "")),
            Math.max(1L, readLong(root, "durationMs", 1L)),
            Math.max(0.0D, readDouble(root, "strength", 0.0D)),
            Math.max(0L, readLong(root, "fadeInMs", 0L)),
            Math.max(0L, readLong(root, "fadeOutMs", 0L)),
            readBoolean(root, "debugLog", false),
            new ScreenEffectPayload.Color(
                clampUnit(readDouble(colorObject, "r", 1.0D)),
                clampUnit(readDouble(colorObject, "g", 1.0D)),
                clampUnit(readDouble(colorObject, "b", 1.0D)),
                clampUnit(readDouble(colorObject, "a", 0.0D))
            ),
            parseShader(shaderObject)
        );
    }

    private static VirtualCameraConfig parseVirtualCameraConfig(JsonObject root) {
        LinkedHashMap<String, VirtualCameraConfig.WorldPosition> resolvedEntityLookAts = new LinkedHashMap<>();
        if (root.has("resolvedEntityLookAts") && root.get("resolvedEntityLookAts").isJsonObject()) {
            JsonObject rawResolvedLookAts = root.getAsJsonObject("resolvedEntityLookAts");
            for (String key : rawResolvedLookAts.keySet()) {
                JsonElement rawResolved = rawResolvedLookAts.get(key);
                if (!rawResolved.isJsonObject()) {
                    LOGGER.warn("EltenaEffect ignored non-object resolvedEntityLookAt entry for key={}", key);
                    continue;
                }
                resolvedEntityLookAts.put(key, parseWorldPosition(rawResolved.getAsJsonObject()));
            }
        }

        LinkedHashMap<String, VirtualCameraConfig.Preset> presets = new LinkedHashMap<>();
        if (root.has("presets") && root.get("presets").isJsonArray()) {
            JsonArray rawPresets = root.getAsJsonArray("presets");
            int index = 0;
            for (JsonElement rawPreset : rawPresets) {
                if (!rawPreset.isJsonObject()) {
                    LOGGER.warn("EltenaEffect ignored non-object virtual camera preset at payload index={}", index);
                    index++;
                    continue;
                }
                JsonObject presetObject = rawPreset.getAsJsonObject();
                VirtualCameraConfig.Preset preset = parseVirtualCameraPreset(presetObject);
                if (preset != null) {
                    presets.put(preset.id(), preset);
                }
                index++;
            }
        }

        return new VirtualCameraConfig(
            readBoolean(root, "enabled", false),
            readBoolean(root, "playerCameraEnabled", false),
            readString(root, "defaultPreset", ""),
            readString(root, "activePreset", ""),
            Math.max(0L, readLong(root, "sceneStartTimeMs", 0L)),
            resolvedEntityLookAts,
            presets
        );
    }

    private static VirtualCameraConfig.Preset parseVirtualCameraPreset(JsonObject presetObject) {
        String id = readString(presetObject, "id", "");
        if (id.isBlank()) {
            LOGGER.warn("EltenaEffect ignored virtual camera preset with blank id.");
            return null;
        }

        String mode = readString(presetObject, "mode", "relative_player");
        boolean fixedWorldMode = "fixed_world".equalsIgnoreCase(mode);
        boolean keyframedMode = "keyframed".equalsIgnoreCase(mode);
        JsonObject relativePositionObject = presetObject.has("relativePosition") && presetObject.get("relativePosition").isJsonObject()
            ? presetObject.getAsJsonObject("relativePosition")
            : new JsonObject();
        JsonObject worldPositionObject = presetObject.has("worldPosition") && presetObject.get("worldPosition").isJsonObject()
            ? presetObject.getAsJsonObject("worldPosition")
            : new JsonObject();
        JsonObject rotationObject = presetObject.has("rotation") && presetObject.get("rotation").isJsonObject()
            ? presetObject.getAsJsonObject("rotation")
            : new JsonObject();
        JsonObject movementObject = presetObject.has("movement") && presetObject.get("movement").isJsonObject()
            ? presetObject.getAsJsonObject("movement")
            : new JsonObject();
        JsonObject smoothingObject = presetObject.has("smoothing") && presetObject.get("smoothing").isJsonObject()
            ? presetObject.getAsJsonObject("smoothing")
            : new JsonObject();
        JsonObject behaviorObject = presetObject.has("behavior") && presetObject.get("behavior").isJsonObject()
            ? presetObject.getAsJsonObject("behavior")
            : new JsonObject();
        List<VirtualCameraConfig.Keyframe> keyframes = keyframedMode ? parseKeyframes(presetObject, id) : List.of();

        return new VirtualCameraConfig.Preset(
            id,
            readString(presetObject, "displayName", id),
            readBoolean(presetObject, "enabled", true),
            mode,
            fixedWorldMode || keyframedMode
                ? null
                : new VirtualCameraConfig.RelativePosition(
                    Math.max(1.0D, readDouble(relativePositionObject, "distance", 6.0D)),
                    readDouble(relativePositionObject, "verticalOffset", 2.2D),
                    readDouble(relativePositionObject, "horizontalOffset", 1.2D),
                    readDouble(relativePositionObject, "forwardOffset", 0.0D)
                ),
            fixedWorldMode ? parseWorldPosition(worldPositionObject) : null,
            keyframedMode ? null : new VirtualCameraConfig.Rotation(
                (float) readDouble(rotationObject, "yaw", 0.0D),
                (float) readDouble(rotationObject, "pitch", 0.0D),
                (float) readDouble(rotationObject, "roll", 0.0D)
            ),
            new VirtualCameraConfig.Movement(
                readBoolean(movementObject, "allowPlayerMovement", true),
                readBoolean(movementObject, "allowCameraMovement", false),
                readBoolean(movementObject, "lockPlayerInput", false)
            ),
            new VirtualCameraConfig.Smoothing(
                readBoolean(smoothingObject, "enabled", true),
                Math.max(0.0D, readDouble(smoothingObject, "positionSmoothTime", 0.18D)),
                Math.max(0.0D, readDouble(smoothingObject, "rotationSmoothTime", 0.12D))
            ),
            new VirtualCameraConfig.Behavior(
                readBoolean(behaviorObject, "resetOnDisable", true),
                readBoolean(behaviorObject, "restorePlayerCameraOnDisable", true),
                readBoolean(behaviorObject, "useVirtualEntityCamera", true)
            ),
            keyframes
        );
    }

    private static List<VirtualCameraConfig.Keyframe> parseKeyframes(JsonObject presetObject, String presetId) {
        if (!presetObject.has("keyframes") || !presetObject.get("keyframes").isJsonArray()) {
            return List.of();
        }

        JsonArray rawKeyframes = presetObject.getAsJsonArray("keyframes");
        List<VirtualCameraConfig.Keyframe> parsedKeyframes = new ArrayList<>();
        int index = 0;
        for (JsonElement rawKeyframe : rawKeyframes) {
            if (!rawKeyframe.isJsonObject()) {
                LOGGER.warn("EltenaEffect ignored non-object keyframe for preset={} at payload index={}", presetId, index);
                index++;
                continue;
            }

            JsonObject keyframeObject = rawKeyframe.getAsJsonObject();
            JsonObject worldPositionObject = keyframeObject.has("worldPosition") && keyframeObject.get("worldPosition").isJsonObject()
                ? keyframeObject.getAsJsonObject("worldPosition")
                : null;
            JsonObject lookAtObject = keyframeObject.has("lookAt") && keyframeObject.get("lookAt").isJsonObject()
                ? keyframeObject.getAsJsonObject("lookAt")
                : null;
            Double fov = keyframeObject.has("fov")
                && keyframeObject.get("fov").isJsonPrimitive()
                && keyframeObject.get("fov").getAsJsonPrimitive().isNumber()
                ? keyframeObject.get("fov").getAsDouble()
                : null;

            parsedKeyframes.add(new VirtualCameraConfig.Keyframe(
                Math.max(0L, readLong(keyframeObject, "timeMs", 0L)),
                worldPositionObject == null ? null : parseWorldPosition(worldPositionObject),
                parseLookAt(lookAtObject, presetId, index),
                fov,
                readString(keyframeObject, "easing", "linear")
            ));
            index++;
        }
        return List.copyOf(parsedKeyframes);
    }

    private static VirtualCameraConfig.LookAt parseLookAt(JsonObject lookAtObject, String presetId, int keyframeIndex) {
        if (lookAtObject == null) {
            return null;
        }

        String type = readString(lookAtObject, "type", "");
        if ("world".equalsIgnoreCase(type)) {
            return new VirtualCameraConfig.WorldLookAt(
                readDouble(lookAtObject, "x", 0.0D),
                readDouble(lookAtObject, "y", 0.0D),
                readDouble(lookAtObject, "z", 0.0D)
            );
        }
        if ("entity".equalsIgnoreCase(type)) {
            return new VirtualCameraConfig.EntityLookAt(
                readString(lookAtObject, "entityRef", ""),
                readDouble(lookAtObject, "offsetX", 0.0D),
                readDouble(lookAtObject, "offsetY", 0.0D),
                readDouble(lookAtObject, "offsetZ", 0.0D)
            );
        }

        LOGGER.warn(
            "EltenaEffect ignored unsupported lookAt type for preset={} at keyframe index={}: {}",
            presetId,
            keyframeIndex,
            type.isBlank() ? "<blank>" : type
        );
        return null;
    }

    private static VirtualCameraConfig.WorldPosition parseWorldPosition(JsonObject object) {
        return new VirtualCameraConfig.WorldPosition(
            readString(object, "world", ""),
            readDouble(object, "x", 0.0D),
            readDouble(object, "y", 0.0D),
            readDouble(object, "z", 0.0D)
        );
    }

    private static ScreenEffectPayload.Shader parseShader(JsonObject object) {
        boolean enabled = readBoolean(object, "enabled", false);
        if (!enabled) {
            return ScreenEffectPayload.Shader.disabled();
        }

        return new ScreenEffectPayload.Shader(
            true,
            ScreenShaderType.fromPayload(readString(object, "type", "")),
            Math.max(0.0D, readDouble(object, "strength", 0.0D)),
            clampInt(readInt(object, "samples", 0), 0, 24),
            Math.max(0.0D, readDouble(object, "radius", 0.0D)),
            Math.max(0.0D, readDouble(object, "chromaticOffset", 0.0D)),
            Math.max(0.0D, readDouble(object, "pressureDistortion", 0.0D)),
            parseShaderLayers(object)
        );
    }

    private static List<ScreenEffectPayload.Shader.Layer> parseShaderLayers(JsonObject object) {
        if (!object.has("layers") || !object.get("layers").isJsonArray()) {
            return List.of();
        }

        JsonArray rawLayers = object.getAsJsonArray("layers");
        List<ScreenEffectPayload.Shader.Layer> parsedLayers = new ArrayList<>();
        int index = 0;
        for (JsonElement rawLayer : rawLayers) {
            if (!rawLayer.isJsonObject()) {
                LOGGER.warn("EltenaEffect ignored non-object shader layer at payload index={}", index);
                index++;
                continue;
            }
            JsonObject layerObject = rawLayer.getAsJsonObject();
            ScreenShaderType layerType = ScreenShaderType.fromPayload(readString(layerObject, "type", ""));
            if (layerType == ScreenShaderType.NONE) {
                LOGGER.warn("EltenaEffect ignored unsupported shader layer type at payload index={}", index);
                index++;
                continue;
            }
            parsedLayers.add(new ScreenEffectPayload.Shader.Layer(
                layerType,
                Math.max(0.0D, readDouble(layerObject, "strength", 0.0D)),
                clampInt(readInt(layerObject, "samples", 0), 0, 24),
                Math.max(0.0D, readDouble(layerObject, "radius", 0.0D)),
                Math.max(0.0D, readDouble(layerObject, "frequency", 0.0D)),
                Math.max(0.0D, readDouble(layerObject, "speed", 0.0D)),
                readBoolean(layerObject, "edgeOnly", false),
                ScreenShaderDirection.fromPayload(readString(layerObject, "direction", "")),
                clampUnit(readDouble(layerObject, "startProgress", 0.0D)),
                clampUnit(readDouble(layerObject, "endProgress", 1.0D)),
                ScreenShaderCurve.fromPayload(readString(layerObject, "curve", "")),
                parseLayerColor(layerObject),
                Math.max(0.0D, readDouble(layerObject, "noiseStrength", 0.0D)),
                Math.max(0.0D, readDouble(layerObject, "wobble", 0.0D)),
                Math.max(0.0D, readDouble(layerObject, "blurScale", 0.0D)),
                Math.max(0.0D, readDouble(layerObject, "refractionStrength", 0.0D)),
                Math.max(0.0D, readDouble(layerObject, "desaturation", 0.0D)),
                Math.max(0.0D, readDouble(layerObject, "smear", 0.0D)),
                Math.max(0.0D, readDouble(layerObject, "pulse", 0.0D)),
                clampInt(readInt(layerObject, "count", 0), 0, 128),
                Math.max(0.0D, readDouble(layerObject, "length", 0.0D)),
                Math.max(0.0D, readDouble(layerObject, "sharpness", 0.0D)),
                Math.max(0.0D, readDouble(layerObject, "centerBias", 0.0D)),
                Math.max(0.0D, readDouble(layerObject, "edgeBias", 0.0D)),
                Math.max(0.0D, readDouble(layerObject, "chromaticOffset", 0.0D))
            ));
            index++;
        }
        return List.copyOf(parsedLayers);
    }

    private static ScreenEffectPayload.Color parseLayerColor(JsonObject layerObject) {
        JsonObject colorObject = layerObject.has("color") && layerObject.get("color").isJsonObject()
            ? layerObject.getAsJsonObject("color")
            : new JsonObject();
        return new ScreenEffectPayload.Color(
            clampUnit(readDouble(colorObject, "r", 1.0D)),
            clampUnit(readDouble(colorObject, "g", 1.0D)),
            clampUnit(readDouble(colorObject, "b", 1.0D)),
            clampUnit(readDouble(colorObject, "a", 0.0D))
        );
    }

    private static String readString(JsonObject object, String key, String fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    private static double readDouble(JsonObject object, String key, double fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isNumber()
            ? object.get(key).getAsDouble()
            : fallback;
    }

    private static long readLong(JsonObject object, String key, long fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isNumber()
            ? object.get(key).getAsLong()
            : fallback;
    }

    private static int readInt(JsonObject object, String key, int fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isNumber()
            ? object.get(key).getAsInt()
            : fallback;
    }

    private static boolean readBoolean(JsonObject object, String key, boolean fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isBoolean()
            ? object.get(key).getAsBoolean()
            : fallback;
    }

    private static double clampUnit(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isDebugEnabled() {
        return Boolean.getBoolean(DEBUG_PROPERTY) || Boolean.getBoolean(LEGACY_DEBUG_PROPERTY);
    }

    private static String translate(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
