package com.eltena.core.application.player;

import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.player.FinalPlayerStats;
import com.eltena.core.domain.player.PlayerProfile;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.Objects;

public final class PlayerManaRegenerationService {

    private final ServiceRegistry services;
    private BukkitTask task;

    public PlayerManaRegenerationService(ServiceRegistry services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    public void start() {
        stop();
        if (!isEnabled()) {
            return;
        }
        long intervalTicks = Math.max(1L, services.plugin().getConfig().getLong("mana.regeneration.interval-ticks", 100L));
        task = services.plugin().getServer().getScheduler().runTaskTimer(
            services.plugin(),
            this::tick,
            intervalTicks,
            intervalTicks
        );
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (!isEnabled()) {
            return;
        }
        int amount = Math.max(0, services.plugin().getConfig().getInt("mana.regeneration.amount", 1));
        if (amount <= 0) {
            return;
        }
        for (Player player : services.plugin().getServer().getOnlinePlayers()) {
            regenerate(player, amount);
        }
    }

    private void regenerate(Player player, int amount) {
        try {
            PlayerProfile profile = services.playerProfiles().loadOrCreate(player.getUniqueId(), player.getName());
            FinalPlayerStats finalStats = services.finalPlayerStatsCalculator().calculate(profile);
            PlayerManaService.ManaChangeResult result = services.playerManaService().recover(
                player.getUniqueId(),
                amount,
                finalStats
            );
            if (!result.changed()) {
                return;
            }
            if (isDebugEnabled()) {
                services.plugin().getLogger().info(
                    "[EltenaCore] MP regen player=" + player.getName()
                        + " before=" + result.before().currentMana()
                        + " after=" + result.after().currentMana()
                        + " max=" + result.after().maxMana()
                        + " reason=natural_regen"
                );
            }
            services.modSyncPluginMessenger().sendPlayerState(player);
        } catch (IOException exception) {
            services.plugin().getLogger().warning(
                "[EltenaCore] Failed to regenerate MP for " + player.getName() + ": " + exception.getMessage()
            );
        }
    }

    private boolean isEnabled() {
        return services.plugin().getConfig().getBoolean("mana.regeneration.enabled", true);
    }

    private boolean isDebugEnabled() {
        return services.plugin().getConfig().getBoolean(
            "mana.regeneration.debug",
            services.plugin().getConfig().getBoolean("combat.debug", false)
        );
    }
}
