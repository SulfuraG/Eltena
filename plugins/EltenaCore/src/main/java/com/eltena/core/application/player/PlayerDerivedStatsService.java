package com.eltena.core.application.player;

import com.eltena.core.application.equipment.MmoItemsEquipmentBonuses;
import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.player.FinalPlayerStats;
import com.eltena.core.domain.player.PlayerProfile;
import com.eltena.core.domain.stats.PlayerStats;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Objects;

public final class PlayerDerivedStatsService {

    private static final String[] MAX_HEALTH_ATTRIBUTE_NAMES = {
        "MAX_HEALTH",
        "GENERIC_MAX_HEALTH"
    };

    private final ServiceRegistry services;
    private Attribute maxHealthAttribute;
    private boolean maxHealthAttributeResolved;

    public PlayerDerivedStatsService(ServiceRegistry services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    public void apply(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            PlayerProfile profile = services.playerProfiles().loadOrCreate(player.getUniqueId(), player.getName());
            PlayerStats stats = services.playerStats().loadOrCreate(player.getUniqueId());
            MmoItemsEquipmentBonuses equipmentBonuses = services.mmoItemsEquipmentStatsProvider().resolve(profile);
            FinalPlayerStats finalStats = services.finalPlayerStatsCalculator().calculate(profile, equipmentBonuses);
            double finalMaxHp = services.finalPlayerStatsCalculator().calculateFinalMaxHpExact(stats, finalStats);

            applyMaxHealth(player, finalMaxHp);
        } catch (IOException exception) {
            services.plugin().getLogger().warning(
                "[EltenaCore] Failed to apply derived stats for " + player.getName() + ": " + exception.getMessage()
            );
        }
    }

    public void applyAndSync(Player player) {
        apply(player);
        if (player != null && player.isOnline()) {
            services.modSyncPluginMessenger().sendPlayerState(player);
        }
    }

    public void syncOnly(Player player) {
        if (player != null && player.isOnline()) {
            services.modSyncPluginMessenger().sendPlayerState(player);
        }
    }

    private void applyMaxHealth(Player player, double finalMaxHp) {
        Attribute maxHealth = resolveMaxHealthAttribute();
        if (maxHealth == null) {
            return;
        }
        AttributeInstance attribute = player.getAttribute(maxHealth);
        if (attribute == null) {
            return;
        }
        double nextMaxHealth = Math.max(1.0D, finalMaxHp);
        if (Math.abs(attribute.getBaseValue() - nextMaxHealth) > 0.001D) {
            attribute.setBaseValue(nextMaxHealth);
        }
        if (player.getHealth() > nextMaxHealth) {
            player.setHealth(nextMaxHealth);
        }
        if (services.growthSettings().combatDebugEnabled()) {
            services.growthSettings().combatDebugLog(
                "HP適用: player=" + player.getName()
                    + " liveMaxHealth=" + attribute.getValue()
                    + " coreBaseMaxHealth=" + nextMaxHealth
            );
        }
    }

    private Attribute resolveMaxHealthAttribute() {
        if (maxHealthAttributeResolved) {
            return maxHealthAttribute;
        }
        maxHealthAttributeResolved = true;
        maxHealthAttribute = resolveAttribute(MAX_HEALTH_ATTRIBUTE_NAMES);
        return maxHealthAttribute;
    }

    private Attribute resolveAttribute(String[] candidates) {
        for (String candidate : candidates) {
            try {
                Field field = Attribute.class.getField(candidate);
                Object value = field.get(null);
                if (value instanceof Attribute attribute) {
                    return attribute;
                }
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                // Try the next compatible enum name for the active server runtime.
            }
        }
        return null;
    }
}
