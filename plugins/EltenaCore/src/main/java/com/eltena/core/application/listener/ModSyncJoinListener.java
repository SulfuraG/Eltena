package com.eltena.core.application.listener;

import com.eltena.core.application.logging.CoreLoggingSettings;
import com.eltena.core.integrations.mod.ModSyncPluginMessenger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class ModSyncJoinListener implements Listener {
    private static final long[] PLAYER_STATE_RETRY_TICKS = {20L, 60L, 120L};

    private final JavaPlugin plugin;
    private final ModSyncPluginMessenger messenger;

    public ModSyncJoinListener(JavaPlugin plugin, ModSyncPluginMessenger messenger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.messenger = Objects.requireNonNull(messenger, "messenger");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (CoreLoggingSettings.syncDebugEnabled(plugin)) {
            plugin.getLogger().info("[EltenaCore] player join同期予約: player=" + event.getPlayer().getName());
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> sendInitialBundle(event.getPlayer().getName()), PLAYER_STATE_RETRY_TICKS[0]);
        for (int index = 1; index < PLAYER_STATE_RETRY_TICKS.length; index++) {
            long delay = PLAYER_STATE_RETRY_TICKS[index];
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> resendPlayerState(event.getPlayer().getName(), delay), delay);
        }
    }

    private void sendInitialBundle(String playerName) {
        var player = plugin.getServer().getPlayerExact(playerName);
        if (player == null || !player.isOnline()) {
            return;
        }
        if (CoreLoggingSettings.syncDebugEnabled(plugin)) {
            plugin.getLogger().info("[EltenaCore] 初回同期送信: player=" + player.getName() + " delayTicks=" + PLAYER_STATE_RETRY_TICKS[0]);
        }
        messenger.sendInitialSyncBundle(player);
    }

    private void resendPlayerState(String playerName, long delay) {
        var player = plugin.getServer().getPlayerExact(playerName);
        if (player == null || !player.isOnline()) {
            return;
        }
        if (CoreLoggingSettings.syncDebugEnabled(plugin)) {
            plugin.getLogger().info("[EltenaCore] player_state再送: player=" + player.getName() + " delayTicks=" + delay);
        }
        messenger.sendPlayerState(player);
    }
}
