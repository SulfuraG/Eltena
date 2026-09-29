package com.eltena.sound.network;

import com.eltena.sound.sound.ClientSoundCategory;
import com.eltena.sound.sound.LiveAssetManifestEntry;
import com.eltena.sound.sound.LiveAssetTransferChunkPayload;
import com.eltena.sound.sound.LiveAssetTransferCompletePayload;
import com.eltena.sound.sound.LiveAssetTransferStartPayload;
import com.eltena.sound.sound.PlayLiveAssetPayload;
import com.eltena.sound.sound.PlaySoundPayload;
import com.eltena.sound.sound.SoundConfigPayload;
import com.eltena.sound.sound.SoundPlaybackManager;
import com.eltena.sound.sound.StopSoundPayload;
import com.eltena.sound.sound.VanillaMusicControlConfig;
import com.eltena.sound.sound.VanillaMusicControlMode;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import org.slf4j.Logger;

public final class EltenaSoundNetwork {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEBUG_PROPERTY = "eltenasound.debug";
    private static final String LEGACY_DEBUG_PROPERTY = "eltena.debug";
    private static final long DEFAULT_STOP_FADE_OUT_MS = 1000L;
    private static final String PROTOCOL_VERSION = "1";
    private static final int READY_DELAY_TICKS = 20;

    private static boolean readyPending;
    private static boolean readySent;
    private static int readyDelayTicks;

    private EltenaSoundNetwork() {
    }

    public static void bootstrap(IEventBus modBus, IEventBus forgeBus) {
        modBus.addListener(EltenaSoundNetwork::onRegisterPayloadHandlers);
        forgeBus.addListener(EltenaSoundNetwork::onClientLoggingIn);
        forgeBus.addListener(EltenaSoundNetwork::onClientLoggingOut);
        forgeBus.addListener(EltenaSoundNetwork::onClientTick);
    }

    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playBidirectional(
            SoundS2CPayload.TYPE,
            SoundS2CPayload.STREAM_CODEC,
            new DirectionalPayloadHandler<>(
                (payload, context) -> {
                    try {
                        if (isDebugEnabled()) {
                            LOGGER.info(
                                translate(
                                    "log.eltenasound.transport",
                                    Integer.toString(payload.byteLength()),
                                    Integer.toString(payload.jsonByteLength())
                                )
                            );
                        }
                        handleIncomingSound(payload.json());
                    } catch (RuntimeException exception) {
                        String message = exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage();
                        LOGGER.error(translate("log.eltenasound.decode_error", message), exception);
                    }
                },
                (payload, context) -> {
                }
            )
        );
    }

    private static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        readyPending = true;
        readySent = false;
        readyDelayTicks = READY_DELAY_TICKS;
    }

    private static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        readyPending = false;
        readySent = false;
        readyDelayTicks = 0;
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (!readyPending || readySent) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null || minecraft.getConnection() == null) {
            return;
        }
        if (readyDelayTicks > 0) {
            readyDelayTicks--;
            return;
        }
        sendReadyPayload();
        readySent = true;
        readyPending = false;
    }

    private static void sendReadyPayload() {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "sound_client_ready");
        payload.addProperty("protocolVersion", PROTOCOL_VERSION);
        payload.addProperty("supportsLiveAssets", true);
        PacketDistributor.sendToServer(SoundS2CPayload.ofJson(payload.toString()));
    }

    private static void handleIncomingSound(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return;
        }

        JsonObject root = JsonParser.parseString(rawJson).getAsJsonObject();
        String type = readString(root, "type", "");
        if ("play_sound".equalsIgnoreCase(type)) {
            PlaySoundPayload payload = parsePlaySound(root);
            if (isDebugEnabled()) {
                LOGGER.info(translate("log.eltenasound.received_play", payload.soundId()));
            }
            SoundPlaybackManager.enqueuePlay(payload);
            return;
        }
        if ("stop_sound".equalsIgnoreCase(type)) {
            StopSoundPayload payload = parseStopSound(root);
            if (isDebugEnabled()) {
                LOGGER.info(translate("log.eltenasound.received_stop", payload.category().name()));
            }
            SoundPlaybackManager.enqueueStop(payload);
            return;
        }
        if ("sound_config".equalsIgnoreCase(type)) {
            SoundConfigPayload payload = parseSoundConfig(root);
            if (isDebugEnabled()) {
                LOGGER.info(
                    translate(
                        "log.eltenasound.received_config",
                        Boolean.toString(payload.vanillaMusicControl().enabled()),
                        payload.vanillaMusicControl().mode().name()
                    )
                );
            }
            SoundPlaybackManager.enqueueConfig(payload);
            return;
        }
        if ("live_asset_manifest".equalsIgnoreCase(type)) {
            List<LiveAssetManifestEntry> assets = parseLiveAssetManifest(root);
            if (isDebugEnabled()) {
                LOGGER.info(translate("log.eltenasound.received_manifest", Integer.toString(assets.size())));
            }
            SoundPlaybackManager.enqueueLiveManifest(assets);
            return;
        }
        if ("play_live_asset".equalsIgnoreCase(type)) {
            PlayLiveAssetPayload payload = parsePlayLiveAsset(root);
            if (isDebugEnabled()) {
                LOGGER.info(translate("log.eltenasound.received_live_play", payload.assetId()));
            }
            SoundPlaybackManager.enqueueLivePlay(payload);
            return;
        }
        if ("live_asset_transfer_start".equalsIgnoreCase(type)) {
            LiveAssetTransferStartPayload payload = parseTransferStart(root);
            if (isDebugEnabled()) {
                LOGGER.info(
                    translate(
                        "log.eltenasound.received_transfer_start",
                        payload.assetId(),
                        Integer.toString(payload.totalChunks())
                    )
                );
            }
            SoundPlaybackManager.enqueueLiveTransferStart(payload);
            return;
        }
        if ("live_asset_transfer_chunk".equalsIgnoreCase(type)) {
            LiveAssetTransferChunkPayload payload = parseTransferChunk(root);
            if (isDebugEnabled()) {
                LOGGER.info(
                    translate(
                        "log.eltenasound.received_transfer_chunk",
                        payload.assetId(),
                        Integer.toString(payload.index())
                    )
                );
            }
            SoundPlaybackManager.enqueueLiveTransferChunk(payload);
            return;
        }
        if ("live_asset_transfer_complete".equalsIgnoreCase(type)) {
            LiveAssetTransferCompletePayload payload = parseTransferComplete(root);
            if (isDebugEnabled()) {
                LOGGER.info(translate("log.eltenasound.received_transfer_complete", payload.assetId()));
            }
            SoundPlaybackManager.enqueueLiveTransferComplete(payload);
            return;
        }

        LOGGER.warn(
            translate(
                "log.eltenasound.unknown_type",
                type.isBlank() ? "<blank>" : type
            )
        );
    }

    private static PlaySoundPayload parsePlaySound(JsonObject root) {
        String rawCategory = readString(root, "category", "SYSTEM");
        ClientSoundCategory category = resolveCategory(rawCategory);
        return new PlaySoundPayload(
            readString(root, "soundId", "unknown"),
            readString(root, "soundEvent", ""),
            category,
            readBoolean(root, "loop", false),
            Math.max(0.0D, readDouble(root, "volume", 1.0D)),
            Math.max(0.01D, readDouble(root, "pitch", 1.0D)),
            Math.max(0L, readLong(root, "fadeInMs", 0L)),
            Math.max(0L, readLong(root, "fadeOutMs", 0L))
        );
    }

    private static StopSoundPayload parseStopSound(JsonObject root) {
        String rawCategory = readString(root, "category", "SYSTEM");
        ClientSoundCategory category = resolveCategory(rawCategory);
        return new StopSoundPayload(
            category,
            Math.max(0L, readLong(root, "fadeOutMs", DEFAULT_STOP_FADE_OUT_MS))
        );
    }

    private static SoundConfigPayload parseSoundConfig(JsonObject root) {
        JsonObject vanillaMusicControl = readObject(root, "vanillaMusicControl");
        if (vanillaMusicControl == null) {
            return SoundConfigPayload.defaults();
        }
        VanillaMusicControlConfig config = new VanillaMusicControlConfig(
            readBoolean(vanillaMusicControl, "enabled", true),
            VanillaMusicControlMode.fromPayload(
                readString(
                    vanillaMusicControl,
                    "mode",
                    VanillaMusicControlMode.ALWAYS_SUPPRESS.name()
                )
            ),
            readBoolean(vanillaMusicControl, "stopOnPlay", false),
            readBoolean(vanillaMusicControl, "stopOnJoin", true),
            readBoolean(vanillaMusicControl, "restoreWhenNoEltenaBgm", false),
            Math.max(1, (int) readLong(vanillaMusicControl, "intervalTicks", 20L))
        );
        return new SoundConfigPayload(config).normalized();
    }

    private static List<LiveAssetManifestEntry> parseLiveAssetManifest(JsonObject root) {
        List<LiveAssetManifestEntry> assets = new ArrayList<>();
        if (!root.has("assets") || !root.get("assets").isJsonArray()) {
            return assets;
        }
        for (var element : root.getAsJsonArray("assets")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject asset = element.getAsJsonObject();
            assets.add(
                new LiveAssetManifestEntry(
                    readString(asset, "id", ""),
                    readString(asset, "assetType", "voice"),
                    Math.max(1, (int) readLong(asset, "version", 1L)),
                    readString(asset, "sha256", ""),
                    readString(asset, "fileName", ""),
                    Math.max(0L, readLong(asset, "sizeBytes", 0L)),
                    resolveCategory(readString(asset, "category", "VOICE"))
                )
            );
        }
        return assets;
    }

    private static PlayLiveAssetPayload parsePlayLiveAsset(JsonObject root) {
        return new PlayLiveAssetPayload(
            readString(root, "assetId", ""),
            readString(root, "assetType", "voice"),
            Math.max(1, (int) readLong(root, "version", 1L)),
            readString(root, "sha256", ""),
            readString(root, "fileName", ""),
            resolveCategory(readString(root, "category", "VOICE")),
            Math.max(0.0D, readDouble(root, "volume", 1.0D)),
            Math.max(0.01D, readDouble(root, "pitch", 1.0D))
        );
    }

    private static LiveAssetTransferStartPayload parseTransferStart(JsonObject root) {
        return new LiveAssetTransferStartPayload(
            readString(root, "assetId", ""),
            readString(root, "assetType", "voice"),
            readString(root, "fileName", ""),
            Math.max(1, (int) readLong(root, "version", 1L)),
            readString(root, "sha256", ""),
            Math.max(0L, readLong(root, "sizeBytes", 0L)),
            Math.max(1, (int) readLong(root, "chunkSize", 1L)),
            Math.max(0, (int) readLong(root, "totalChunks", 0L)),
            resolveCategory(readString(root, "category", "VOICE"))
        );
    }

    private static LiveAssetTransferChunkPayload parseTransferChunk(JsonObject root) {
        return new LiveAssetTransferChunkPayload(
            readString(root, "assetId", ""),
            Math.max(0, (int) readLong(root, "index", 0L)),
            readString(root, "dataBase64", "")
        );
    }

    private static LiveAssetTransferCompletePayload parseTransferComplete(JsonObject root) {
        return new LiveAssetTransferCompletePayload(readString(root, "assetId", ""));
    }

    private static ClientSoundCategory resolveCategory(String rawCategory) {
        ClientSoundCategory category = ClientSoundCategory.tryFromPayload(rawCategory);
        if (category != null) {
            return category;
        }
        if (isDebugEnabled()) {
            LOGGER.warn(
                translate(
                    "log.eltenasound.unknown_category",
                    rawCategory == null || rawCategory.isBlank() ? "<blank>" : rawCategory
                )
            );
        }
        return ClientSoundCategory.SYSTEM;
    }

    private static String readString(JsonObject object, String key, String fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    private static JsonObject readObject(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull() || !object.get(key).isJsonObject()) {
            return null;
        }
        return object.getAsJsonObject(key);
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

    private static boolean readBoolean(JsonObject object, String key, boolean fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isBoolean()
            ? object.get(key).getAsBoolean()
            : fallback;
    }

    private static boolean isDebugEnabled() {
        return Boolean.getBoolean(DEBUG_PROPERTY) || Boolean.getBoolean(LEGACY_DEBUG_PROPERTY);
    }

    private static String translate(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
