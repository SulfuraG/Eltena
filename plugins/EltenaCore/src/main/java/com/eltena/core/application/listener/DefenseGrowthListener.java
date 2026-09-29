package com.eltena.core.application.listener;

import com.eltena.core.application.growth.GrowthSettings;
import com.eltena.core.bootstrap.ServiceRegistry;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.eltena.core.domain.stats.GrowthType.DEFENSE;

public final class DefenseGrowthListener implements Listener {

    private static final Set<EntityDamageEvent.DamageCause> IGNORED_CAUSES = EnumSet.of(
        EntityDamageEvent.DamageCause.VOID,
        EntityDamageEvent.DamageCause.FALL,
        EntityDamageEvent.DamageCause.SUICIDE,
        EntityDamageEvent.DamageCause.KILL
    );

    private final ServiceRegistry services;
    private final GrowthSettings settings;
    private final Map<UUID, Integer> cooldowns = new HashMap<>();

    public DefenseGrowthListener(ServiceRegistry services, GrowthSettings settings) {
        this.services = services;
        this.settings = settings;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!settings.defenseEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getFinalDamage() <= 0.0D || IGNORED_CAUSES.contains(event.getCause())) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent damageByEntity) {
            Entity damager = damageByEntity.getDamager();
            if (damager instanceof Player) {
                return;
            }
        }

        int tick = player.getTicksLived();
        if (settings.isOnCooldown(cooldowns, player.getUniqueId(), tick, settings.defenseCooldownTicks())) {
            return;
        }
        settings.markTriggered(cooldowns, player.getUniqueId(), tick);

        long amount = resolveDefenseGain(player, event);
        try {
            services.statGrowthSystem().addGrowth(player.getUniqueId(), DEFENSE, amount);
            settings.debugLog(player.getName() + " が被ダメージで防御成長 +" + amount + " cause=" + event.getCause());
        } catch (IOException exception) {
            services.plugin().getLogger().warning("防御成長の保存に失敗しました: " + exception.getMessage());
        }
    }

    private long resolveDefenseGain(Player player, EntityDamageEvent event) {
        if (player.isBlocking()) {
            return settings.shieldDefenseExp();
        }
        if (event instanceof EntityDamageByEntityEvent damageByEntity && damageByEntity.getDamager() instanceof LivingEntity) {
            return settings.mobDamageDefenseExp();
        }
        return settings.normalDamageDefenseExp();
    }
}
