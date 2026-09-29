package com.eltena.core.application.listener;

import com.eltena.core.application.progression.MythicMobDropInspector;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Objects;

public final class MobDropControlListener implements Listener {

    private final MythicMobDropInspector mythicMobDropInspector;

    public MobDropControlListener(MythicMobDropInspector mythicMobDropInspector) {
        this.mythicMobDropInspector = Objects.requireNonNull(mythicMobDropInspector, "mythicMobDropInspector");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player || !(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }

        MythicMobDropInspector.MythicMobDropContext dropContext = mythicMobDropInspector.inspect(livingEntity);
        if (dropContext.mythicMob()) {
            if (!dropContext.hasExplicitDrops()) {
                event.getDrops().clear();
            }
            return;
        }

        event.getDrops().clear();
    }
}
