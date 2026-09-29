package com.eltena.core.application.progression;

import com.eltena.core.application.growth.GrowthSettings;
import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.integrations.mobhp.EltenaMobHpBridge;
import io.lumine.mythic.api.config.MythicConfig;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;

import java.util.Objects;

public final class MobKillExperienceService {

    private final ServiceRegistry services;
    private final GrowthSettings settings;
    private final EltenaMobHpBridge mobHpBridge;

    public MobKillExperienceService(ServiceRegistry services, GrowthSettings settings) {
        this.services = Objects.requireNonNull(services, "services");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.mobHpBridge = new EltenaMobHpBridge(services.plugin().getLogger());
    }

    public long resolveFor(LivingEntity entity) {
        if (!settings.killExpEnabled() || entity == null) {
            return 0L;
        }

        String displayTargetName = resolveDisplayTargetName(entity);
        if (!displayTargetName.isBlank()) {
            Long byName = settings.killExpByName().get(displayTargetName);
            if (byName != null) {
                return byName;
            }
        }

        MythicMobContext mythicMob = resolveMythicMob(entity);
        if (settings.mythicKillExpEnabled() && mythicMob != null) {
            Long byInternalName = settings.mythicKillExpByInternalName().get(settings.normalizeMobId(mythicMob.internalName()));
            if (byInternalName != null) {
                return byInternalName;
            }

            if (settings.mythicKillExpUseOptionsExperience() && mythicMob.hasConfiguredExperience()) {
                if (mythicMob.optionsExperience() > 0L || settings.mythicKillExpZeroExperienceMeansZero()) {
                    return mythicMob.optionsExperience();
                }
            }

            return settings.mythicKillExpFallback();
        }

        Long byType = settings.killExpByEntityType().get(entity.getType());
        return byType != null ? byType : settings.killExpVanillaDefault();
    }

    public String resolveDisplayTargetName(LivingEntity entity) {
        if (entity == null) {
            return "";
        }
        String customName = entity.getCustomName();
        if (customName == null || customName.isBlank()) {
            MythicMobContext mythicMob = resolveMythicMob(entity);
            if (mythicMob == null || mythicMob.displayName().isBlank()) {
                return "";
            }
            return normalizeVisibleName(mobHpBridge.stripHpSuffix(mythicMob.displayName()));
        }
        return normalizeVisibleName(mobHpBridge.stripHpSuffix(customName));
    }

    private MythicMobContext resolveMythicMob(LivingEntity entity) {
        try {
            MythicBukkit mythic = MythicBukkit.inst();
            if (mythic == null) {
                return null;
            }
            BukkitAPIHelper helper = mythic.getAPIHelper();
            if (helper == null) {
                return null;
            }
            ActiveMob activeMob = helper.getMythicMobInstance(entity);
            if (activeMob == null) {
                return null;
            }
            MythicMob type = activeMob.getType();
            if (type == null) {
                return null;
            }
            MythicConfig config = type.getConfig();
            boolean hasConfiguredExperience = config != null && config.isSet("Options.Experience");
            long optionsExperience = hasConfiguredExperience ? Math.max(0L, config.getLong("Options.Experience", 0L)) : 0L;
            String displayName = "";
            if (type.getDisplayName() != null) {
                String resolved = type.getDisplayName().get();
                if (resolved != null) {
                    displayName = resolved;
                }
            }
            if (displayName.isBlank()) {
                displayName = activeMob.getDisplayName();
            }
            return new MythicMobContext(type.getInternalName(), displayName == null ? "" : displayName, hasConfiguredExperience, optionsExperience);
        } catch (RuntimeException exception) {
            services.plugin().getLogger().warning("[EltenaCore] Failed to inspect MythicMobs kill EXP target: " + exception.getMessage());
            return null;
        }
    }

    private String normalizeVisibleName(String rawName) {
        String stripped = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', rawName));
        return stripped == null ? "" : settings.normalizeDisplayName(stripped);
    }

    private record MythicMobContext(
        String internalName,
        String displayName,
        boolean hasConfiguredExperience,
        long optionsExperience
    ) {
    }
}
