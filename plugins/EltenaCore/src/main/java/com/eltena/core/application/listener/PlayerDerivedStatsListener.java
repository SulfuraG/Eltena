package com.eltena.core.application.listener;

import com.eltena.core.application.player.PlayerDerivedStatsService;
import com.eltena.core.bootstrap.ServiceRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.Objects;

public final class PlayerDerivedStatsListener implements Listener {

    private final ServiceRegistry services;
    private final PlayerDerivedStatsService derivedStatsService;

    public PlayerDerivedStatsListener(ServiceRegistry services, PlayerDerivedStatsService derivedStatsService) {
        this.services = Objects.requireNonNull(services, "services");
        this.derivedStatsService = Objects.requireNonNull(derivedStatsService, "derivedStatsService");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        scheduleApplyAndSync(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        scheduleApplyAndSync(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        scheduleApplyAndSync(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        scheduleSyncOnly(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        scheduleSyncOnly(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleSyncOnly(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleSyncOnly(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            scheduleSyncOnly(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getItem() != null) {
            scheduleSyncOnly(event.getPlayer());
        }
    }

    private void scheduleApplyAndSync(Player player) {
        if (player == null) {
            return;
        }
        services.plugin().getServer().getScheduler().runTask(
            services.plugin(),
            () -> derivedStatsService.applyAndSync(player)
        );
    }

    private void scheduleSyncOnly(Player player) {
        if (player == null) {
            return;
        }
        services.plugin().getServer().getScheduler().runTask(
            services.plugin(),
            () -> derivedStatsService.syncOnly(player)
        );
    }
}
