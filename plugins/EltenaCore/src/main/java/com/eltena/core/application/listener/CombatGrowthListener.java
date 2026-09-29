package com.eltena.core.application.listener;

import com.eltena.core.application.growth.GrowthSettings;
import com.eltena.core.application.progression.ProgressionResult;
import com.eltena.core.bootstrap.ServiceRegistry;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.io.IOException;

public final class CombatGrowthListener implements Listener {

    private final ServiceRegistry services;
    private final GrowthSettings settings;

    public CombatGrowthListener(ServiceRegistry services, GrowthSettings settings) {
        this.services = services;
        this.settings = settings;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            event.setDroppedExp(0);
        }

        Player killer = event.getEntity().getKiller();
        if (killer == null || event.getEntity() instanceof Player || !(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }

        long levelExpAmount = services.mobKillExperienceService().resolveFor(livingEntity);

        try {
            ProgressionResult progressionResult = levelExpAmount > 0L
                ? services.levelSystem().addExperience(killer.getUniqueId(), killer.getName(), levelExpAmount)
                : null;
            if (progressionResult != null) {
                sendKillExperienceMessage(killer, livingEntity, progressionResult.gainedExperience());
            }
            services.modSyncPluginMessenger().sendPlayerState(killer);
            services.modSyncPluginMessenger().sendAbilityState(killer);
            services.modSyncPluginMessenger().sendSkillTree(killer);

            if (progressionResult != null && settings.debugEventsEnabled()) {
                services.plugin().getLogger().info(
                    "[EltenaCore] Kill EXP granted: "
                        + killer.getName()
                        + " +"
                        + progressionResult.gainedExperience()
                        + " from "
                        + event.getEntityType()
                        + " exp="
                        + progressionResult.profile().experience()
                        + "/"
                        + services.levelSystem().requiredExpFor(progressionResult.profile().level())
                        + " level="
                        + progressionResult.profile().level()
                );
            }
            settings.debugLog(
                killer.getName()
                    + " gained kill exp from "
                    + event.getEntityType()
                    + " level-exp +"
                    + levelExpAmount
            );
        } catch (IOException exception) {
            services.plugin().getLogger().warning("[EltenaCore] Failed to grant kill progression: " + exception.getMessage());
        }
    }

    private void sendKillExperienceMessage(Player killer, LivingEntity target, long amount) {
        if (killer == null || amount <= 0L) {
            return;
        }
        if (!services.playerOptionSettingsService().isExpChatEnabled(killer.getUniqueId())) {
            return;
        }
        String targetName = services.mobKillExperienceService().resolveDisplayTargetName(target);
        String key = targetName.isBlank() ? "growth.exp.kill-gained" : "growth.exp.kill-gained-named";
        killer.sendMessage(color(services.messages().get(key, "amount", amount, "target", targetName)));
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
