package com.eltena.core.application.player;

import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.player.FinalPlayerStats;
import com.eltena.core.domain.stats.PlayerStats;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class PlayerManaService {

    private static final int DEFAULT_CURRENT_MANA = 10;
    private static final int DEFAULT_MAX_MANA = 20;

    private final ServiceRegistry services;

    public PlayerManaService(ServiceRegistry services) {
        this.services = services;
    }

    public ManaSnapshot resolve(UUID playerId) throws IOException {
        return resolve(playerId, 0);
    }

    public ManaSnapshot resolve(UUID playerId, FinalPlayerStats finalStats) throws IOException {
        return resolve(playerId, finalStats == null ? 0 : finalStats.maxMpBonus());
    }

    public ManaSnapshot resolve(UUID playerId, int derivedMaxManaBonus) throws IOException {
        PlayerStats stats = migrateIfNeeded(services.playerStats().loadOrCreate(playerId));
        int maxMana = effectiveMaxMana(stats.maxMp(), derivedMaxManaBonus);
        int currentMana = Math.max(0, Math.min(stats.mp(), maxMana));
        return new ManaSnapshot(currentMana, maxMana);
    }

    public ManaSnapshot consume(UUID playerId, int amount) throws IOException {
        return consume(playerId, amount, 0);
    }

    public ManaSnapshot consume(UUID playerId, int amount, FinalPlayerStats finalStats) throws IOException {
        return consume(playerId, amount, finalStats == null ? 0 : finalStats.maxMpBonus());
    }

    public ManaSnapshot consume(UUID playerId, int amount, int derivedMaxManaBonus) throws IOException {
        PlayerStats stats = migrateIfNeeded(services.playerStats().loadOrCreate(playerId));
        int effectiveMaxMana = effectiveMaxMana(stats.maxMp(), derivedMaxManaBonus);
        int currentMana = Math.max(0, Math.min(stats.mp(), effectiveMaxMana));
        int nextMana = Math.max(0, currentMana - Math.max(0, amount));
        PlayerStats updated = stats.withMana(nextMana, stats.maxMpExact(), true);
        services.playerStats().save(updated);
        return new ManaSnapshot(nextMana, effectiveMaxMana);
    }

    public ManaChangeResult recover(UUID playerId, int amount) throws IOException {
        return recover(playerId, amount, 0);
    }

    public ManaChangeResult recover(UUID playerId, int amount, FinalPlayerStats finalStats) throws IOException {
        return recover(playerId, amount, finalStats == null ? 0 : finalStats.maxMpBonus());
    }

    public ManaChangeResult recover(UUID playerId, int amount, int derivedMaxManaBonus) throws IOException {
        PlayerStats stats = migrateIfNeeded(services.playerStats().loadOrCreate(playerId));
        int effectiveMaxMana = effectiveMaxMana(stats.maxMp(), derivedMaxManaBonus);
        int currentMana = Math.max(0, Math.min(stats.mp(), effectiveMaxMana));
        ManaSnapshot before = new ManaSnapshot(currentMana, effectiveMaxMana);
        int recovery = Math.max(0, amount);
        int nextMana = Math.min(effectiveMaxMana, currentMana + recovery);
        if (nextMana == currentMana) {
            return new ManaChangeResult(before, before, false);
        }
        PlayerStats updated = stats.withMana(nextMana, stats.maxMpExact(), true);
        services.playerStats().save(updated);
        ManaSnapshot after = new ManaSnapshot(nextMana, effectiveMaxMana);
        return new ManaChangeResult(before, after, true);
    }

    public ManaChangeResult restoreFull(UUID playerId) throws IOException {
        return restoreFull(playerId, 0);
    }

    public ManaChangeResult restoreFull(UUID playerId, FinalPlayerStats finalStats) throws IOException {
        return restoreFull(playerId, finalStats == null ? 0 : finalStats.maxMpBonus());
    }

    public ManaChangeResult restoreFull(UUID playerId, int derivedMaxManaBonus) throws IOException {
        PlayerStats stats = migrateIfNeeded(services.playerStats().loadOrCreate(playerId));
        int effectiveMaxMana = effectiveMaxMana(stats.maxMp(), derivedMaxManaBonus);
        int currentMana = Math.max(0, Math.min(stats.mp(), effectiveMaxMana));
        ManaSnapshot before = new ManaSnapshot(currentMana, effectiveMaxMana);
        if (currentMana == effectiveMaxMana) {
            return new ManaChangeResult(before, before, false);
        }
        PlayerStats updated = stats.withMana(effectiveMaxMana, stats.maxMpExact(), true);
        services.playerStats().save(updated);
        ManaSnapshot after = new ManaSnapshot(effectiveMaxMana, effectiveMaxMana);
        return new ManaChangeResult(before, after, true);
    }

    public int migrateAllPlayers() {
        Set<UUID> playerIds = collectKnownPlayerIds();
        int migratedCount = 0;
        for (UUID playerId : playerIds) {
            try {
                PlayerStats stats = services.playerStats().loadOrCreate(playerId);
                PlayerStats migrated = migrateIfNeeded(stats);
                if (migrated.manaMigrated() && !stats.manaMigrated()) {
                    migratedCount++;
                }
            } catch (IOException exception) {
                services.plugin().getLogger().warning(
                    "[EltenaCore] Failed to migrate MP for " + playerId + ": " + exception.getMessage()
                );
            }
        }
        return migratedCount;
    }

    private PlayerStats migrateIfNeeded(PlayerStats stats) throws IOException {
        if (stats.manaMigrated()) {
            return stats;
        }

        UUID playerId = stats.playerId();
        int currentMana = sanitizeCurrentMana(stats.mp(), DEFAULT_CURRENT_MANA);
        int maxMana = sanitizeMaxMana(stats.maxMp(), Math.max(currentMana, DEFAULT_MAX_MANA));

        File auraSkillsFolder = new File(services.plugin().getDataFolder().getParentFile(), "AuraSkills");
        if (auraSkillsFolder.isDirectory()) {
            File userDataFile = new File(new File(auraSkillsFolder, "userdata"), playerId + ".yml");
            if (userDataFile.isFile()) {
                YamlConfiguration userData = YamlConfiguration.loadConfiguration(userDataFile);
                currentMana = sanitizeCurrentMana(
                    (int) Math.round(userData.getDouble("mana", currentMana)),
                    currentMana
                );
            }

            File statsFile = new File(auraSkillsFolder, "stats.yml");
            if (statsFile.isFile()) {
                YamlConfiguration auraStats = YamlConfiguration.loadConfiguration(statsFile);
                int configuredBaseMana = sanitizeMaxMana(
                    (int) Math.round(auraStats.getDouble("traits.auraskills/max_mana.base", maxMana)),
                    maxMana
                );
                maxMana = Math.max(maxMana, configuredBaseMana);
            }
        }

        maxMana = Math.max(maxMana, currentMana);
        PlayerStats migrated = stats.withMana(currentMana, maxMana, true);
        services.playerStats().save(migrated);
        services.plugin().getLogger().info(
            "[EltenaCore] Migrated MP from AuraSkills fallback for " + playerId
                + " -> " + migrated.mp() + "/" + migrated.maxMp()
        );
        return migrated;
    }

    private Set<UUID> collectKnownPlayerIds() {
        Set<UUID> playerIds = new LinkedHashSet<>();
        collectPlayerIdsFromDirectories(playerIds, new File(services.plugin().getDataFolder(), "players"), true);
        File auraSkillsFolder = new File(services.plugin().getDataFolder().getParentFile(), "AuraSkills");
        collectPlayerIdsFromDirectories(playerIds, new File(auraSkillsFolder, "userdata"), false);
        return playerIds;
    }

    private void collectPlayerIdsFromDirectories(Set<UUID> playerIds, File folder, boolean directories) {
        if (folder == null || !folder.isDirectory()) {
            return;
        }
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (directories && !file.isDirectory()) {
                continue;
            }
            if (!directories && !file.isFile()) {
                continue;
            }
            String raw = directories ? file.getName() : trimYamlSuffix(file.getName());
            try {
                playerIds.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private String trimYamlSuffix(String fileName) {
        if (fileName.endsWith(".yml")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        if (fileName.endsWith(".yaml")) {
            return fileName.substring(0, fileName.length() - 5);
        }
        return fileName;
    }

    private int sanitizeCurrentMana(int value, int fallback) {
        return Math.max(0, value >= 0 ? value : fallback);
    }

    private int sanitizeMaxMana(int value, int fallback) {
        return value > 0 ? value : Math.max(1, fallback);
    }

    private int effectiveMaxMana(int baseMaxMana, int derivedMaxManaBonus) {
        return Math.max(1, baseMaxMana + Math.max(0, derivedMaxManaBonus));
    }

    public record ManaSnapshot(int currentMana, int maxMana) {
    }

    public record ManaChangeResult(ManaSnapshot before, ManaSnapshot after, boolean changed) {
    }
}
