package com.eltena.core.infrastructure.persistence.stats;

import com.eltena.core.api.stats.PlayerStatsRepository;
import com.eltena.core.application.growth.GrowthSettings;
import com.eltena.core.domain.stats.GrowthType;
import com.eltena.core.domain.stats.PlayerStats;
import com.eltena.core.domain.stats.StatGrowthProgress;
import com.eltena.core.domain.stats.WeaponType;
import com.eltena.core.domain.stats.WeaponMasteryProgress;
import com.eltena.core.infrastructure.cache.PlayerStatsCache;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class YamlPlayerStatsRepository implements PlayerStatsRepository {

    private final JavaPlugin plugin;
    private final PlayerStatsCache cache;
    private final GrowthSettings growthSettings;

    public YamlPlayerStatsRepository(JavaPlugin plugin, PlayerStatsCache cache, GrowthSettings growthSettings) {
        this.plugin = plugin;
        this.cache = cache;
        this.growthSettings = growthSettings;
    }

    @Override
    public PlayerStats loadOrCreate(UUID playerId) throws IOException {
        PlayerStats cached = cache.get(playerId);
        if (cached != null) {
            return cached;
        }

        File file = statsFile(playerId);
        if (!file.exists()) {
            PlayerStats created = createDefault(playerId);
            save(created);
            return created;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        PlayerStats defaults = createDefault(playerId);
        PlayerStats stats = new PlayerStats(
            playerId,
            yaml.getDouble("base.hp-exact", yaml.getDouble("base.hp", defaults.hpExact())),
            yaml.getInt("base.mp", defaults.mp()),
            yaml.getDouble(
                "base.max-mp-exact",
                yaml.getDouble("base.max-mp", Math.max(yaml.getDouble("base.mp", defaults.mp()), defaults.maxMpExact()))
            ),
            yaml.getBoolean("meta.mana-migrated", false),
            yaml.getInt("base.attack", defaults.attack()),
            yaml.getInt("base.defense", defaults.defense()),
            loadWeaponMastery(yaml.getConfigurationSection("weapon-mastery")),
            loadStringIntMap(yaml.getConfigurationSection("job-affinity")),
            loadStringIntMap(yaml.getConfigurationSection("sub-growth")),
            loadStatGrowth(yaml.getConfigurationSection("stat-growth"))
        );
        cache.put(stats);
        return stats;
    }

    @Override
    public PlayerStats save(PlayerStats stats) throws IOException {
        File file = statsFile(stats.playerId());
        ensureParent(file);

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("base.hp", stats.hp());
        yaml.set("base.hp-exact", stats.hpExact());
        yaml.set("base.mp", stats.mp());
        yaml.set("base.max-mp", stats.maxMp());
        yaml.set("base.max-mp-exact", stats.maxMpExact());
        yaml.set("base.attack", stats.attack());
        yaml.set("base.defense", stats.defense());
        yaml.set("meta.mana-migrated", stats.manaMigrated());

        stats.weaponMastery().forEach((type, value) -> {
            yaml.set("weapon-mastery." + type.name() + ".level", value.level());
            yaml.set("weapon-mastery." + type.name() + ".exp", value.experience());
        });
        stats.jobAffinity().forEach((jobId, value) -> yaml.set("job-affinity." + jobId, value));
        stats.subGrowth().forEach((growthId, value) -> yaml.set("sub-growth." + growthId, value));
        stats.statGrowth().forEach((type, value) -> {
            yaml.set("stat-growth." + type.key() + ".level", value.level());
            yaml.set("stat-growth." + type.key() + ".exp", value.experience());
        });
        yaml.save(file);

        cache.put(stats);
        return stats;
    }

    @Override
    public void invalidate(UUID playerId) {
        cache.invalidate(playerId);
    }

    private File statsFile(UUID playerId) {
        return new File(playersDirectory(playerId), "stats.yml");
    }

    private File playersDirectory(UUID playerId) {
        return new File(plugin.getDataFolder(), "players" + File.separator + playerId);
    }

    private void ensureParent(File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    private Map<WeaponType, WeaponMasteryProgress> loadWeaponMastery(ConfigurationSection section) {
        EnumMap<WeaponType, WeaponMasteryProgress> values = new EnumMap<>(WeaponType.class);
        for (WeaponType type : WeaponType.values()) {
            if (section == null) {
                values.put(type, WeaponMasteryProgress.initial());
                continue;
            }

            Object legacyValue = section.get(type.name());
            if (legacyValue instanceof Number number) {
                values.put(type, new WeaponMasteryProgress(number.intValue(), 0L));
                continue;
            }

            ConfigurationSection masterySection = section.getConfigurationSection(type.name());
            if (masterySection == null) {
                values.put(type, WeaponMasteryProgress.initial());
                continue;
            }

            values.put(type, new WeaponMasteryProgress(
                masterySection.getInt("level", 0),
                masterySection.getLong("exp", 0L)
            ));
        }
        return values;
    }

    private Map<GrowthType, StatGrowthProgress> loadStatGrowth(ConfigurationSection section) {
        EnumMap<GrowthType, StatGrowthProgress> values = new EnumMap<>(GrowthType.class);
        for (GrowthType type : GrowthType.values()) {
            ConfigurationSection progress = section == null ? null : section.getConfigurationSection(type.key());
            if (progress == null) {
                values.put(type, StatGrowthProgress.initial());
                continue;
            }
            values.put(type, new StatGrowthProgress(
                progress.getInt("level", 0),
                progress.getLong("exp", 0L)
            ));
        }
        return values;
    }

    private Map<String, Integer> loadStringIntMap(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Integer> values = new HashMap<>();
        for (String key : section.getKeys(false)) {
            values.put(key, section.getInt(key, 0));
        }
        return values;
    }

    private PlayerStats createDefault(UUID playerId) {
        EnumMap<WeaponType, WeaponMasteryProgress> weaponMastery = new EnumMap<>(WeaponType.class);
        for (WeaponType type : WeaponType.values()) {
            weaponMastery.put(type, WeaponMasteryProgress.initial());
        }

        EnumMap<GrowthType, StatGrowthProgress> growth = new EnumMap<>(GrowthType.class);
        for (GrowthType type : GrowthType.values()) {
            growth.put(type, StatGrowthProgress.initial());
        }

        return new PlayerStats(
            playerId,
            growthSettings.baseHpExact(),
            growthSettings.baseMp(),
            Math.max(growthSettings.baseMp(), growthSettings.baseMaxMpExact()),
            false,
            growthSettings.baseAttack(),
            growthSettings.baseDefense(),
            weaponMastery,
            Map.of("novice", 0),
            Map.of("vitality", 0, "focus", 0, "agility", 0),
            growth
        );
    }
}
