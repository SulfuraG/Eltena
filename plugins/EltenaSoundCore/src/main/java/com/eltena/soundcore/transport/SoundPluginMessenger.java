package com.eltena.soundcore.transport;

import com.eltena.soundcore.EltenaSoundCorePlugin;
import com.eltena.soundcore.listener.LiveAssetRequestListener;
import com.eltena.soundcore.live.LiveAssetDefinition;
import com.eltena.soundcore.config.VanillaMusicControlSettings;
import com.eltena.soundcore.sound.SoundCategory;
import com.eltena.soundcore.sound.SoundDefinition;
import java.util.Map;
import org.bukkit.entity.Player;

public final class SoundPluginMessenger {
    public static final String SOUND_CHANNEL_NAME = "eltena:sound";

    private final EltenaSoundCorePlugin plugin;

    public SoundPluginMessenger(EltenaSoundCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void registerChannel() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, SOUND_CHANNEL_NAME);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(
            plugin,
            SOUND_CHANNEL_NAME,
            new LiveAssetRequestListener(plugin)
        );
    }

    public void unregisterChannel() {
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, SOUND_CHANNEL_NAME);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, SOUND_CHANNEL_NAME);
    }

    public void sendPlay(Player player, SoundDefinition sound, byte[] bytes) {
        player.sendPluginMessage(plugin, SOUND_CHANNEL_NAME, bytes);
        if (plugin.isDebugLogEnabled()) {
            plugin.logInfo(
                "log.dispatch-play",
                Map.of(
                    "soundId", sound.id(),
                    "player", player.getName(),
                    "bytes", Integer.toString(bytes.length),
                    "category", sound.category().name()
                )
            );
        }
    }

    public void sendStop(Player player, SoundCategory category, long fadeOutMs, byte[] bytes) {
        player.sendPluginMessage(plugin, SOUND_CHANNEL_NAME, bytes);
        if (plugin.isDebugLogEnabled()) {
            plugin.logInfo(
                "log.dispatch-stop",
                Map.of(
                    "player", player.getName(),
                    "bytes", Integer.toString(bytes.length),
                    "category", category.name(),
                    "fadeOutMs", Long.toString(fadeOutMs)
                )
            );
        }
    }

    public void sendConfig(Player player, VanillaMusicControlSettings settings, byte[] bytes) {
        player.sendPluginMessage(plugin, SOUND_CHANNEL_NAME, bytes);
        if (plugin.isDebugLogEnabled()) {
            plugin.logInfo(
                "log.dispatch-config",
                Map.of(
                    "player", player.getName(),
                    "bytes", Integer.toString(bytes.length),
                    "enabled", Boolean.toString(settings.enabled()),
                    "mode", settings.mode(),
                    "intervalTicks", Integer.toString(settings.intervalTicks())
                )
            );
        }
    }

    public void sendLiveAssetManifest(Player player, int assetCount, byte[] bytes) {
        player.sendPluginMessage(plugin, SOUND_CHANNEL_NAME, bytes);
        if (plugin.isDebugLogEnabled()) {
            plugin.logInfo(
                "log.manifest-sent",
                Map.of(
                    "player", player.getName(),
                    "bytes", Integer.toString(bytes.length),
                    "assetCount", Integer.toString(assetCount)
                )
            );
        }
    }

    public void sendPlayLiveAsset(Player player, LiveAssetDefinition asset, byte[] bytes) {
        player.sendPluginMessage(plugin, SOUND_CHANNEL_NAME, bytes);
        if (plugin.isDebugLogEnabled()) {
            plugin.logInfo(
                "log.dispatch-live-play",
                Map.of(
                    "assetId", asset.id(),
                    "player", player.getName(),
                    "bytes", Integer.toString(bytes.length),
                    "category", asset.category().name()
                )
            );
        }
    }

    public void sendLiveAssetTransferStart(
        Player player,
        LiveAssetDefinition asset,
        int totalChunks,
        byte[] bytes
    ) {
        player.sendPluginMessage(plugin, SOUND_CHANNEL_NAME, bytes);
        if (plugin.isDebugLogEnabled()) {
            plugin.logInfo(
                "log.transfer-start",
                Map.of(
                    "assetId", asset.id(),
                    "player", player.getName(),
                    "bytes", Integer.toString(bytes.length),
                    "totalChunks", Integer.toString(totalChunks)
                )
            );
        }
    }

    public void sendLiveAssetTransferChunk(
        Player player,
        LiveAssetDefinition asset,
        int chunkIndex,
        int totalChunks,
        byte[] bytes
    ) {
        player.sendPluginMessage(plugin, SOUND_CHANNEL_NAME, bytes);
        if (plugin.isDebugLogEnabled()) {
            plugin.logInfo(
                "log.transfer-chunk-progress",
                Map.of(
                    "assetId", asset.id(),
                    "player", player.getName(),
                    "chunkIndex", Integer.toString(chunkIndex),
                    "totalChunks", Integer.toString(totalChunks),
                    "bytes", Integer.toString(bytes.length)
                )
            );
        }
    }

    public void sendLiveAssetTransferComplete(Player player, LiveAssetDefinition asset, byte[] bytes) {
        player.sendPluginMessage(plugin, SOUND_CHANNEL_NAME, bytes);
        if (plugin.isDebugLogEnabled()) {
            plugin.logInfo(
                "log.transfer-complete",
                Map.of(
                    "assetId", asset.id(),
                    "player", player.getName(),
                    "bytes", Integer.toString(bytes.length)
                )
            );
        }
    }
}
