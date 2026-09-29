package com.eltena.core.application.progression;

import com.eltena.core.bootstrap.ServiceRegistry;
import io.lumine.mythic.api.config.MythicConfig;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.entity.LivingEntity;

import java.util.Objects;

public final class MythicMobDropInspector {

    private final ServiceRegistry services;

    public MythicMobDropInspector(ServiceRegistry services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    public MythicMobDropContext inspect(LivingEntity entity) {
        if (entity == null) {
            return MythicMobDropContext.nonMythic();
        }

        try {
            MythicBukkit mythic = MythicBukkit.inst();
            if (mythic == null) {
                return MythicMobDropContext.nonMythic();
            }
            BukkitAPIHelper helper = mythic.getAPIHelper();
            if (helper == null) {
                return MythicMobDropContext.nonMythic();
            }
            ActiveMob activeMob = helper.getMythicMobInstance(entity);
            if (activeMob == null) {
                return MythicMobDropContext.nonMythic();
            }

            MythicMob type = activeMob.getType();
            if (type == null) {
                return new MythicMobDropContext(true, false, "");
            }

            MythicConfig config = type.getConfig();
            boolean hasExplicitDrops = hasExplicitDrops(config);
            return new MythicMobDropContext(true, hasExplicitDrops, type.getInternalName());
        } catch (RuntimeException exception) {
            services.plugin().getLogger().warning("[EltenaCore] Failed to inspect MythicMobs drop policy: " + exception.getMessage());
            return MythicMobDropContext.nonMythic();
        }
    }

    private boolean hasExplicitDrops(MythicConfig config) {
        if (config == null) {
            return false;
        }
        return config.isSet("Drops")
            || config.isSet("DropTables")
            || config.isSet("DropTable");
    }

    public record MythicMobDropContext(
        boolean mythicMob,
        boolean hasExplicitDrops,
        String internalName
    ) {
        public static MythicMobDropContext nonMythic() {
            return new MythicMobDropContext(false, false, "");
        }
    }
}
