package com.eltena.core.application.listener;

import com.eltena.core.application.growth.GrowthSettings;
import com.eltena.core.application.growth.WeaponTypeResolver;
import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.player.FinalPlayerStats;
import com.eltena.core.domain.player.PlayerProfile;
import com.eltena.core.domain.stats.WeaponType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WeaponMasteryListener implements Listener {

    private final ServiceRegistry services;
    private final GrowthSettings settings;
    private final WeaponTypeResolver resolver;
    private final Map<UUID, Integer> cooldowns = new HashMap<>();

    public WeaponMasteryListener(ServiceRegistry services, GrowthSettings settings, WeaponTypeResolver resolver) {
        this.services = services;
        this.settings = settings;
        this.resolver = resolver;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!settings.masteryEnabled()) {
            return;
        }
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target) || target instanceof Player) {
            return;
        }
        if (event.getFinalDamage() <= 0.0D) {
            return;
        }

        int tick = player.getTicksLived();
        if (settings.isOnCooldown(cooldowns, player.getUniqueId(), tick, settings.masteryCooldownTicks())) {
            return;
        }
        settings.markTriggered(cooldowns, player.getUniqueId(), tick);

        WeaponType weaponType = resolver.resolve(player.getInventory().getItemInMainHand());
        long amount = settings.masteryAttackExp();
        if (target.getHealth() - event.getFinalDamage() <= 0.0D) {
            amount += settings.masteryKillBonusExp();
        }
        amount = applyMasteryMultiplier(player, amount);

        try {
            services.weaponMasterySystem().addMastery(player.getUniqueId(), weaponType, amount);
            settings.debugLog(player.getName() + " が " + weaponType + " で熟練度 +" + amount);
        } catch (IOException exception) {
            services.plugin().getLogger().warning("武器熟練度の保存に失敗しました: " + exception.getMessage());
        }
    }

    private long applyMasteryMultiplier(Player player, long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        try {
            PlayerProfile profile = services.playerProfiles().loadOrCreate(player.getUniqueId(), player.getName());
            FinalPlayerStats finalStats = services.finalPlayerStatsCalculator().calculate(profile);
            return Math.max(1L, Math.round(amount * finalStats.masteryGainMultiplier()));
        } catch (IOException exception) {
            services.plugin().getLogger().warning(
                "[EltenaCore] Failed to apply mastery multiplier for " + player.getName() + ": " + exception.getMessage()
            );
            return amount;
        }
    }
}
