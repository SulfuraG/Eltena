package com.eltena.core.application.listener;

import com.eltena.core.application.growth.GrowthSettings;
import com.eltena.core.bootstrap.ServiceRegistry;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Set;

public final class ServerRuleListener implements Listener {

    private static final Set<String> NATURAL_SPAWN_REASONS = Set.of(
        "NATURAL",
        "CHUNK_GEN",
        "REINFORCEMENTS",
        "NETHER_PORTAL",
        "PATROL",
        "VILLAGE_INVASION"
    );

    private final ServiceRegistry services;
    private final GrowthSettings settings;

    public ServerRuleListener(ServiceRegistry services, GrowthSettings settings) {
        this.services = services;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (settings.suppressMobExpOrbs() && !(event.getEntity() instanceof Player)) {
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (!settings.suppressExperienceOrbSpawns()) {
            return;
        }
        if (event.getEntity() instanceof ExperienceOrb) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!settings.hungerLockEnabled() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
            applyFoodState(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!settings.blockNaturalMobSpawns()) {
            return;
        }
        if (NATURAL_SPAWN_REASONS.contains(event.getSpawnReason().name())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (settings.hungerLockEnabled() && settings.refillFoodOnJoin()) {
            services.plugin().getServer().getScheduler().runTask(services.plugin(), () -> applyFoodState(event.getPlayer()));
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (settings.hungerLockEnabled() && settings.refillFoodOnRespawn()) {
            services.plugin().getServer().getScheduler().runTask(services.plugin(), () -> applyFoodState(event.getPlayer()));
        }
    }

    private void applyFoodState(Player player) {
        player.setFoodLevel(settings.fixedFoodLevel());
        player.setSaturation(settings.fixedSaturation());
        player.setExhaustion(0.0F);
    }
}
