package com.eltena.soundcore.playback;

import com.eltena.soundcore.EltenaSoundCorePlugin;
import com.eltena.soundcore.config.LiveAssetRegistry;
import com.eltena.soundcore.live.LiveAssetDefinition;
import com.eltena.soundcore.live.LiveAssetDispatchResult;
import com.eltena.soundcore.transport.SoundPluginMessenger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;

public final class LiveAssetPlaybackService {
    private static final int DEFAULT_CHUNK_SIZE = 8192;

    private final EltenaSoundCorePlugin plugin;
    private final LiveAssetRegistry liveAssetRegistry;
    private final SoundPluginMessenger messenger;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public LiveAssetPlaybackService(
        EltenaSoundCorePlugin plugin,
        LiveAssetRegistry liveAssetRegistry,
        SoundPluginMessenger messenger
    ) {
        this.plugin = plugin;
        this.liveAssetRegistry = liveAssetRegistry;
        this.messenger = messenger;
    }

    public List<LiveAssetDefinition> definitions() {
        return liveAssetRegistry.definitions();
    }

    public void syncManifestToOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            sendManifest(player);
        }
    }

    public boolean sendManifest(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (!plugin.isPlayerReady(player)) {
            logSkippedNotReady(player, "live_asset_manifest", "");
            return false;
        }
        byte[] bytes = gson.toJson(buildManifestPayload()).getBytes(StandardCharsets.UTF_8);
        messenger.sendLiveAssetManifest(player, liveAssetRegistry.count(), bytes);
        return true;
    }

    public boolean sendAsset(Player player, String assetId) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (!plugin.isPlayerReady(player)) {
            logSkippedNotReady(player, "live_asset_transfer_start", assetId);
            return false;
        }
        LiveAssetDefinition definition = liveAssetRegistry.require(assetId);
        dispatchTransfer(player, definition);
        return true;
    }

    public LiveAssetDispatchResult play(Player player, String assetId) {
        if (player == null || !player.isOnline()) {
            return new LiveAssetDispatchResult(assetId, assetId, 0, 0);
        }
        if (!plugin.isPlayerReady(player)) {
            logSkippedNotReady(player, "play_live_asset", assetId);
            return new LiveAssetDispatchResult(assetId, assetId, 0, 1);
        }
        LiveAssetDefinition definition = liveAssetRegistry.require(assetId);
        byte[] playBytes = gson.toJson(buildPlayPayload(definition)).getBytes(StandardCharsets.UTF_8);
        messenger.sendPlayLiveAsset(player, definition, playBytes);
        dispatchTransfer(player, definition);
        if (plugin.isDebugLogEnabled()) {
            plugin.logInfo(
                "log.play-live-asset",
                Map.of(
                    "assetId", definition.id(),
                    "category", definition.category().name(),
                    "player", player.getName()
                )
            );
        }
        return new LiveAssetDispatchResult(definition.id(), definition.displayName(), 1, 0);
    }

    private void dispatchTransfer(Player player, LiveAssetDefinition definition) {
        byte[] assetBytes = readAssetBytes(definition);
        int chunkSize = Math.min(DEFAULT_CHUNK_SIZE, Math.max(1024, assetBytes.length == 0 ? DEFAULT_CHUNK_SIZE : DEFAULT_CHUNK_SIZE));
        int totalChunks = assetBytes.length == 0 ? 0 : (int) Math.ceil(assetBytes.length / (double) chunkSize);

        byte[] startBytes = gson.toJson(buildTransferStartPayload(definition, chunkSize, totalChunks))
            .getBytes(StandardCharsets.UTF_8);
        messenger.sendLiveAssetTransferStart(player, definition, totalChunks, startBytes);

        for (int index = 0; index < totalChunks; index++) {
            int offset = index * chunkSize;
            int length = Math.min(chunkSize, assetBytes.length - offset);
            byte[] chunk = java.util.Arrays.copyOfRange(assetBytes, offset, offset + length);
            byte[] chunkBytes = gson.toJson(buildTransferChunkPayload(definition.id(), index, chunk))
                .getBytes(StandardCharsets.UTF_8);
            messenger.sendLiveAssetTransferChunk(player, definition, index + 1, totalChunks, chunkBytes);
        }

        byte[] completeBytes = gson.toJson(buildTransferCompletePayload(definition.id()))
            .getBytes(StandardCharsets.UTF_8);
        messenger.sendLiveAssetTransferComplete(player, definition, completeBytes);
    }

    private byte[] readAssetBytes(LiveAssetDefinition definition) {
        try {
            return Files.readAllBytes(definition.absoluteFilePath());
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Failed to read live asset file: " + definition.absoluteFilePath(),
                exception
            );
        }
    }

    private JsonObject buildManifestPayload() {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "live_asset_manifest");
        JsonArray assets = new JsonArray();
        for (LiveAssetDefinition definition : liveAssetRegistry.definitions()) {
            JsonObject asset = new JsonObject();
            asset.addProperty("id", definition.id());
            asset.addProperty("assetType", definition.type().payloadName());
            asset.addProperty("version", definition.version());
            asset.addProperty("sha256", definition.sha256());
            asset.addProperty("fileName", definition.fileName());
            asset.addProperty("sizeBytes", definition.sizeBytes());
            asset.addProperty("category", definition.category().name());
            assets.add(asset);
        }
        payload.add("assets", assets);
        return payload;
    }

    private JsonObject buildPlayPayload(LiveAssetDefinition definition) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "play_live_asset");
        payload.addProperty("assetId", definition.id());
        payload.addProperty("assetType", definition.type().payloadName());
        payload.addProperty("version", definition.version());
        payload.addProperty("sha256", definition.sha256());
        payload.addProperty("fileName", definition.fileName());
        payload.addProperty("category", definition.category().name());
        payload.addProperty("volume", definition.volume());
        payload.addProperty("pitch", definition.pitch());
        return payload;
    }

    private JsonObject buildTransferStartPayload(
        LiveAssetDefinition definition,
        int chunkSize,
        int totalChunks
    ) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "live_asset_transfer_start");
        payload.addProperty("assetId", definition.id());
        payload.addProperty("assetType", definition.type().payloadName());
        payload.addProperty("fileName", definition.fileName());
        payload.addProperty("version", definition.version());
        payload.addProperty("sha256", definition.sha256());
        payload.addProperty("sizeBytes", definition.sizeBytes());
        payload.addProperty("chunkSize", chunkSize);
        payload.addProperty("totalChunks", totalChunks);
        payload.addProperty("category", definition.category().name());
        return payload;
    }

    private JsonObject buildTransferChunkPayload(String assetId, int index, byte[] chunk) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "live_asset_transfer_chunk");
        payload.addProperty("assetId", assetId);
        payload.addProperty("index", index);
        payload.addProperty("dataBase64", Base64.getEncoder().encodeToString(chunk));
        return payload;
    }

    private JsonObject buildTransferCompletePayload(String assetId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "live_asset_transfer_complete");
        payload.addProperty("assetId", assetId);
        return payload;
    }

    private void logSkippedNotReady(Player player, String payloadType, String assetId) {
        if (!plugin.isDebugLogEnabled() || player == null) {
            return;
        }
        plugin.logInfo(
            "log.payload-skipped-not-ready",
            Map.of(
                "player", player.getName(),
                "payloadType", payloadType,
                "assetId", assetId == null || assetId.isBlank() ? "-" : assetId
            )
        );
    }
}
