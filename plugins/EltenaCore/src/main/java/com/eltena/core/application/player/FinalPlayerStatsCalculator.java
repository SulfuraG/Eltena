package com.eltena.core.application.player;

import com.eltena.core.application.equipment.ArmorDefenseSnapshotResolver;
import com.eltena.core.application.equipment.MmoItemsDefinitionBonuses;
import com.eltena.core.application.equipment.MmoItemsEquipmentBonuses;
import com.eltena.core.application.logging.CoreLoggingSettings;
import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.player.FinalPlayerStats;
import com.eltena.core.domain.player.PlayerProfile;
import com.eltena.core.domain.player.PlayerStatBonuses;
import com.eltena.core.domain.stats.PlayerStats;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class FinalPlayerStatsCalculator {

    private final ServiceRegistry services;
    private final ArmorDefenseSnapshotResolver armorDefenseSnapshotResolver;

    public FinalPlayerStatsCalculator(ServiceRegistry services) {
        this.services = Objects.requireNonNull(services, "services");
        this.armorDefenseSnapshotResolver = new ArmorDefenseSnapshotResolver();
    }

    public FinalPlayerStats calculate(PlayerProfile profile) {
        return calculate(profile, services.mmoItemsEquipmentStatsProvider().resolve(profile));
    }

    public FinalPlayerStats calculate(PlayerProfile profile, MmoItemsEquipmentBonuses mmoItemsEquipment) {
        PlayerStats persistedStats = loadPlayerStats(profile);
        double baseDefense = persistedStats == null ? services.growthSettings().baseDefense() : persistedStats.defense();
        FinalPlayerStats finalStats = new FinalPlayerStats(0.0D, 0.0D, 0.0D, 0.0D, baseDefense, 0.0D, 1.0D, 0.0D, 0, 0.0D, 0.0D, 1.0D, 0, 0.0D);
        if (profile == null) {
            return applyDerivedRoleBonuses(finalStats);
        }

        ArmorDefenseSnapshotResolver.DefenseSnapshot armorSnapshot = resolveArmorSnapshot(profile);
        PlayerStatBonuses equipmentBonuses = resolveEquipmentBonuses(profile, mmoItemsEquipment);
        PlayerStatBonuses armorBonuses = PlayerStatBonuses.single("defense", armorSnapshot.totalArmorDefense());

        finalStats = finalStats.apply(services.skillSystem().resolveStatBonuses(profile));
        finalStats = finalStats.apply(equipmentBonuses);
        finalStats = finalStats.apply(armorBonuses);
        finalStats = finalStats.apply(services.titleSystem().resolveStatBonuses(profile));
        finalStats = finalStats.apply(resolveTemporaryBonuses(profile));

        FinalPlayerStats normalized = applyDerivedRoleBonuses(finalStats);
        logDefenseDebug(profile, baseDefense, equipmentBonuses, mmoItemsEquipment, armorSnapshot, normalized);
        return normalized;
    }

    public int calculateFinalMaxHp(PlayerStats stats, FinalPlayerStats finalStats) {
        return Math.max(1, (int) Math.round(calculateFinalMaxHpExact(stats, finalStats)));
    }

    public double calculateFinalMaxHpExact(PlayerStats stats, FinalPlayerStats finalStats) {
        return calculateFinalMaxHpExact(stats, finalStats, MmoItemsEquipmentBonuses.none());
    }

    public int calculateFinalMaxHp(PlayerStats stats, FinalPlayerStats finalStats, MmoItemsEquipmentBonuses equipmentBonuses) {
        return Math.max(1, (int) Math.round(calculateFinalMaxHpExact(stats, finalStats, equipmentBonuses)));
    }

    public double calculateFinalMaxHpExact(PlayerStats stats, FinalPlayerStats finalStats, MmoItemsEquipmentBonuses equipmentBonuses) {
        if (stats == null) {
            return Math.max(1.0D, services.growthSettings().baseHpExact() + (finalStats == null ? 0 : finalStats.hpBonus()));
        }
        int bonus = finalStats == null ? 0 : finalStats.hpBonus();
        return Math.max(1.0D, stats.hpExact() + bonus);
    }

    public int calculateFinalMaxMp(PlayerStats stats, FinalPlayerStats finalStats) {
        return Math.max(1, (int) Math.round(calculateFinalMaxMpExact(stats, finalStats)));
    }

    public double calculateFinalMaxMpExact(PlayerStats stats, FinalPlayerStats finalStats) {
        if (stats == null) {
            return Math.max(1.0D, services.growthSettings().baseMaxMpExact() + (finalStats == null ? 0 : finalStats.maxMpBonus()));
        }
        int bonus = finalStats == null ? 0 : finalStats.maxMpBonus();
        return Math.max(1.0D, stats.maxMpExact() + bonus);
    }

    private PlayerStatBonuses resolveEquipmentBonuses(PlayerProfile profile, MmoItemsEquipmentBonuses mmoItemsEquipment) {
        if (!mmoItemsEquipment.resolvedItemIds().isEmpty()) {
            PlayerStatBonuses legacy = services.equipmentSystem().resolveStatBonuses(profile, mmoItemsEquipment.resolvedItemIds());
            return legacy.add(mmoItemsEquipment.toPlayerStatBonuses());
        }
        MmoItemsDefinitionBonuses mmoItems = services.mmoItemsDefinitionStatsProvider().resolve(profile);
        Set<String> excludedIds = new LinkedHashSet<>();
        excludedIds.addAll(mmoItems.mappedItemIds());
        PlayerStatBonuses legacy = services.equipmentSystem().resolveStatBonuses(profile, excludedIds);
        return legacy.add(mmoItems.toPlayerStatBonuses());
    }

    private PlayerStatBonuses resolveTemporaryBonuses(PlayerProfile profile) {
        return PlayerStatBonuses.none();
    }

    private ArmorDefenseSnapshotResolver.DefenseSnapshot resolveArmorSnapshot(PlayerProfile profile) {
        if (profile == null) {
            return ArmorDefenseSnapshotResolver.DefenseSnapshot.none();
        }
        Player player = Bukkit.getPlayer(profile.playerId());
        if (player == null || !player.isOnline()) {
            return ArmorDefenseSnapshotResolver.DefenseSnapshot.none();
        }
        return armorDefenseSnapshotResolver.resolve(player, services.mmoItemsEquipmentStatsProvider());
    }

    private FinalPlayerStats applyDerivedRoleBonuses(FinalPlayerStats finalStats) {
        if (finalStats == null) {
            return new FinalPlayerStats(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 1.0D, 0.0D, 0, 0.0D, 0.0D, 1.0D, 0, 0.0D);
        }
        return new FinalPlayerStats(
            0.0D,
            0.0D,
            0.0D,
            0.0D,
            finalStats.defense(),
            0.0D,
            1.0D,
            0.0D,
            0,
            0.0D,
            0.0D,
            1.0D,
            0,
            0.0D
        );
    }

    private PlayerStats loadPlayerStats(PlayerProfile profile) {
        if (profile == null) {
            return null;
        }
        try {
            return services.playerStats().loadOrCreate(profile.playerId());
        } catch (IOException exception) {
            services.plugin().getLogger().warning(
                "[EltenaCore] Failed to load player stats for final defense: "
                    + profile.playerId()
                    + " message=" + exception.getMessage()
            );
            return null;
        }
    }

    private void logDefenseDebug(
        PlayerProfile profile,
        double baseDefense,
        PlayerStatBonuses equipmentBonuses,
        MmoItemsEquipmentBonuses mmoItemsEquipment,
        ArmorDefenseSnapshotResolver.DefenseSnapshot armorSnapshot,
        FinalPlayerStats normalized
    ) {
        if (!CoreLoggingSettings.mmoItemsDebugEnabled(services.plugin()) || profile == null) {
            return;
        }
        services.plugin().getLogger().info(
            "[EltenaCore] 最終防御計算:"
                + " player=" + profile.playerName()
                + " baseDefense=" + baseDefense
                + " vanillaArmorDefense=" + armorSnapshot.vanillaArmorDefense()
                + " modArmorDefense=" + armorSnapshot.modArmorDefense()
                + " liveArmorDefense=" + armorSnapshot.liveArmorDefense()
                + " legacyDefense=" + equipmentBonuses.defense()
                + " mmoitemsDefense=" + mmoItemsEquipment.defense()
                + " mmoitemsMaxHealth=" + mmoItemsEquipment.maxHealthBonus()
                + " finalDefense=" + normalized.defense()
        );
        for (ArmorDefenseSnapshotResolver.SlotDefenseEntry slotEntry : armorSnapshot.slotEntries()) {
            services.plugin().getLogger().info(
                "[EltenaCore] 防御スロット:"
                    + " player=" + profile.playerName()
                    + " slot=" + slotEntry.slot()
                    + " material=" + safeText(slotEntry.materialId())
                    + " itemType=" + safeText(slotEntry.itemType())
                    + " itemId=" + safeText(slotEntry.itemId())
                    + " slotDefense=" + slotEntry.slotDefense()
                    + " modded=" + slotEntry.isModded()
            );
        }
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
