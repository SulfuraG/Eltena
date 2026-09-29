package com.eltena.core.application.listener;

import com.eltena.core.bootstrap.ServiceRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuraSkillsHealthGuardListener implements Listener {

    private final ServiceRegistry services;
    private final Set<UUID> warnedPlayers = ConcurrentHashMap.newKeySet();

    public AuraSkillsHealthGuardListener(ServiceRegistry services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getRegainReason() != null) {
            return;
        }
        event.setCancelled(true);
        if (warnedPlayers.add(player.getUniqueId())) {
            services.plugin().getLogger().warning(
                "[EltenaCore] Cancelled invalid EntityRegainHealthEvent with null regain reason for "
                    + player.getName()
            );
        }
    }
}
