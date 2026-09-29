package com.eltena.soundcore.listener;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import com.eltena.soundcore.EltenaSoundCorePlugin;
import com.eltena.soundcore.transport.SoundPluginMessenger;

public final class LiveAssetRequestListener implements PluginMessageListener {
    private final EltenaSoundCorePlugin plugin;

    public LiveAssetRequestListener(EltenaSoundCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!SoundPluginMessenger.SOUND_CHANNEL_NAME.equals(channel)
            || player == null
            || !player.isOnline()
            || message == null
            || message.length == 0
            || plugin.liveAssetPlaybackService() == null) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(new String(message, StandardCharsets.UTF_8)).getAsJsonObject();
            String type = readString(root, "type");
            if ("sound_client_ready".equalsIgnoreCase(type)) {
                plugin.handleClientReady(
                    player,
                    readString(root, "protocolVersion"),
                    readBoolean(root, "supportsLiveAssets", false)
                );
                return;
            }
            if (!"request_live_asset".equalsIgnoreCase(type)) {
                return;
            }
            String assetId = readString(root, "assetId");
            if (assetId.isBlank()) {
                return;
            }
            if (!plugin.isPlayerReady(player)) {
                if (plugin.isDebugLogEnabled()) {
                    plugin.logInfo(
                        "log.payload-skipped-not-ready",
                        Map.of(
                            "player", player.getName(),
                            "payloadType", "request_live_asset",
                            "assetId", assetId
                        )
                    );
                }
                return;
            }
            if (plugin.isDebugLogEnabled()) {
                plugin.logInfo(
                    "log.live-asset-requested",
                    Map.of(
                        "player", player.getName(),
                        "assetId", assetId
                    )
                );
            }
            plugin.liveAssetPlaybackService().sendAsset(player, assetId);
        } catch (RuntimeException exception) {
            plugin.logWarning(
                "log.live-asset-request-error",
                Map.of(
                    "player", player.getName(),
                    "message", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()
                )
            );
        }
    }

    private String readString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).isJsonPrimitive() ? object.get(key).getAsString().trim() : "";
    }

    private boolean readBoolean(JsonObject object, String key, boolean fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isBoolean()
            ? object.get(key).getAsBoolean()
            : fallback;
    }
}
