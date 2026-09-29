package com.eltena.core.application.listener;

import com.eltena.core.application.growth.GrowthSettings;
import com.eltena.core.bootstrap.ServiceRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;

public final class VanillaExperienceGuardListener implements Listener {

    private final ServiceRegistry services;
    private final GrowthSettings settings;

    public VanillaExperienceGuardListener(ServiceRegistry services, GrowthSettings settings) {
        this.services = services;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerExpChange(PlayerExpChangeEvent event) {
        if (event.getAmount() <= 0) {
            return;
        }
        event.setAmount(0);
        settings.debugLog(event.getPlayer().getName() + " のバニラEXP取得を抑制 amount=" + event.getAmount());
        services.plugin().getLogger().fine("[EltenaCore] Suppressed vanilla exp gain for " + event.getPlayer().getName());
    }
}
