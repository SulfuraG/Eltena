package com.eltena.soundcore.listener;

import com.eltena.soundcore.EltenaSoundCorePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class SoundPlayerSyncListener implements Listener {
    private final EltenaSoundCorePlugin plugin;

    public SoundPlayerSyncListener(EltenaSoundCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.removeReadyPlayer(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.removeReadyPlayer(event.getPlayer());
    }
}
