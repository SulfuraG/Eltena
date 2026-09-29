package com.eltena.core.infrastructure.persistence.player;

import com.eltena.core.application.ability.AbilityCatalog;
import com.eltena.core.application.growth.GrowthSettings;
import com.eltena.core.application.logging.CoreLoggingSettings;
import com.eltena.core.api.player.PlayerProfileRepository;
import com.eltena.core.domain.player.BasePlayerStats;
import com.eltena.core.domain.player.PlayerProfile;
import com.eltena.core.infrastructure.cache.PlayerProfileCache;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class YamlPlayerProfileRepository implements PlayerProfileRepository {

    private final JavaPlugin plugin;
    private final PlayerProfileCache cache;
    private final GrowthSettings growthSettings;

    public YamlPlayerProfileRepository(JavaPlugin plugin, PlayerProfileCache cache, GrowthSettings growthSettings) {
        this.plugin = plugin;
        this.cache = cache;
        this.growthSettings = growthSettings;
    }

    @Override
    public PlayerProfile loadOrCreate(UUID playerId, String playerName) throws IOException {
        PlayerProfile cached = cache.get(playerId);
        if (cached != null) {
            if (!cached.playerName().equals(playerName)) {
                PlayerProfile renamed = cached.withPlayerName(playerName);
                cache.put(renamed);
                save(renamed);
                return renamed;
            }
            return cached;
        }

        File file = profileFile(playerId);
        if (!file.exists()) {
            PlayerProfile created = PlayerProfile.createDefault(playerId, playerName).withStats(defaultAttributes());
            save(created);
            return created;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Set<String> unlockedJobs = loadStringSet(yaml.getStringList("jobs.unlocked"), "novice");
        Set<String> unlockedTitles = loadStringSet(yaml.getStringList("titles.unlocked"), "none");
        Set<String> discoveredExplorations = new LinkedHashSet<>(yaml.getStringList("exploration.discovered"));
        Map<String, Integer> skillRanks = loadSkillRanks(yaml.getConfigurationSection("skills.ranks"));
        Set<String> unlockedAbilities = normalizeAbilitySet(yaml.getStringList("abilities.unlocked"));
        Map<String, String> equippedAbilities = loadAbilityMap(yaml.getConfigurationSection("abilities.equipped"));
        BasePlayerStats attributes = loadAttributes(yaml.getConfigurationSection("stats"));

        PlayerProfile profile = new PlayerProfile(
            playerId,
            yaml.getString("player.name", playerName),
            yaml.getInt("progression.level", 1),
            yaml.getLong("progression.exp", 0L),
            yaml.getString("progression.current-job", "novice"),
            yaml.getString("progression.world-rank", "unranked"),
            yaml.getLong("progression.world-rank-exp", 0L),
            yaml.getString("progression.active-title", "none"),
            yaml.getInt("progression.skill-point", 12),
            unlockedJobs,
            unlockedTitles,
            discoveredExplorations,
            yaml.getInt("exploration.discovery-count", discoveredExplorations.size()),
            skillRanks.isEmpty() ? defaultSkillRanks() : skillRanks,
            unlockedAbilities,
            equippedAbilities,
            attributes,
            parseInstant(yaml.getString("meta.created-at"), Instant.now()),
            parseInstant(yaml.getString("meta.updated-at"), Instant.now())
        );

        if (CoreLoggingSettings.profileDebugEnabled(plugin)) {
            plugin.getLogger().info("[EltenaCore] Loaded abilities: " + profile.unlockedAbilities());
        }

        cache.put(profile);
        if (!yaml.contains("jobs.unlocked")
            || !yaml.contains("titles.unlocked")
            || !yaml.contains("exploration.discovery-count")
            || !yaml.contains("progression.skill-point")
            || !yaml.contains("abilities.unlocked")
            || !yaml.contains("stats")) {
            save(profile);
        }
        return profile;
    }

    @Override
    public PlayerProfile save(PlayerProfile profile) throws IOException {
        File file = profileFile(profile.playerId());
        ensureParent(file);

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("player.uuid", profile.playerId().toString());
        yaml.set("player.name", profile.playerName());
        yaml.set("progression.level", profile.level());
        yaml.set("progression.exp", profile.experience());
        yaml.set("progression.current-job", profile.currentJobId());
        yaml.set("progression.world-rank", profile.worldRankId());
        yaml.set("progression.world-rank-exp", profile.worldRankExperience());
        yaml.set("progression.active-title", profile.activeTitleId());
        yaml.set("progression.skill-point", profile.skillPoint());
        yaml.set("jobs.unlocked", List.copyOf(profile.unlockedJobs()));
        yaml.set("titles.unlocked", List.copyOf(profile.unlockedTitles()));
        yaml.set("exploration.discovered", List.copyOf(profile.discoveredExplorations()));
        yaml.set("exploration.discovery-count", profile.discoveryCount());
        writeSkillRanks(yaml, profile.skillRanks());
        yaml.set("abilities.unlocked", List.copyOf(normalizeAbilitySet(profile.unlockedAbilities())));
        writeAbilityMap(yaml, "abilities.equipped", profile.equippedAbilities());
        writeAttributes(yaml, profile.stats());
        yaml.set("meta.created-at", profile.createdAt().toString());
        yaml.set("meta.updated-at", profile.updatedAt().toString());
        yaml.save(file);

        cache.put(profile);
        return profile;
    }

    @Override
    public void invalidate(UUID playerId) {
        cache.invalidate(playerId);
    }

    private File profileFile(UUID playerId) {
        return new File(playersDirectory(playerId), "profile.yml");
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

    private Instant parseInstant(String raw, Instant fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return Instant.parse(raw);
    }

    private Set<String> loadStringSet(List<String> values, String requiredDefault) {
        LinkedHashSet<String> result = new LinkedHashSet<>(values);
        result.add(requiredDefault);
        return result;
    }

    private Map<String, Integer> loadSkillRanks(ConfigurationSection section) {
        Map<String, Integer> loaded = new LinkedHashMap<>();
        if (section == null) {
            return loaded;
        }
        for (String key : section.getKeys(false)) {
            loaded.put(key, Math.max(0, section.getInt(key, 0)));
        }
        return loaded;
    }

    private void writeSkillRanks(YamlConfiguration yaml, Map<String, Integer> skillRanks) {
        for (Map.Entry<String, Integer> entry : skillRanks.entrySet()) {
            yaml.set("skills.ranks." + entry.getKey(), Math.max(0, entry.getValue()));
        }
    }

    private Map<String, String> loadAbilityMap(ConfigurationSection section) {
        Map<String, String> loaded = new LinkedHashMap<>();
        if (section == null) {
            return loaded;
        }
        for (String key : section.getKeys(false)) {
            String value = AbilityCatalog.normalizeId(section.getString(key));
            if (value != null) {
                loaded.put(key, value);
            }
        }
        return loaded;
    }

    private void writeAbilityMap(YamlConfiguration yaml, String rootPath, Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String normalized = AbilityCatalog.normalizeId(entry.getValue());
            if (normalized == null) {
                continue;
            }
            yaml.set(rootPath + "." + entry.getKey(), normalized);
        }
    }

    private Set<String> normalizeAbilitySet(Iterable<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String normalizedId = AbilityCatalog.normalizeId(value);
            if (normalizedId != null) {
                normalized.add(normalizedId);
            }
        }
        return normalized;
    }

    private Map<String, Integer> defaultSkillRanks() {
        Map<String, Integer> defaults = new LinkedHashMap<>();
        defaults.put("basic_slash", 1);
        defaults.put("quick_step", 1);
        return defaults;
    }

    private BasePlayerStats loadAttributes(ConfigurationSection section) {
        BasePlayerStats defaults = defaultAttributes();
        if (section == null) {
            return defaults;
        }
        return new BasePlayerStats(
            section.getInt("strength", defaults.strength()),
            section.getInt("dexterity", defaults.dexterity()),
            section.getInt("intelligence", defaults.intelligence()),
            section.getInt("vitality", defaults.vitality()),
            section.getInt("defense", defaults.defense()),
            section.getDouble("crit-rate", defaults.critRate()),
            section.getDouble("crit-damage", defaults.critDamage())
        );
    }

    private BasePlayerStats defaultAttributes() {
        return new BasePlayerStats(
            growthSettings.baseStrength(),
            growthSettings.baseDexterity(),
            growthSettings.baseIntelligence(),
            10,
            growthSettings.baseDefense(),
            0.05D,
            1.5D
        );
    }

    private void writeAttributes(YamlConfiguration yaml, BasePlayerStats attributes) {
        yaml.set("stats.strength", attributes.strength());
        yaml.set("stats.dexterity", attributes.dexterity());
        yaml.set("stats.intelligence", attributes.intelligence());
        yaml.set("stats.vitality", attributes.vitality());
        yaml.set("stats.defense", attributes.defense());
        yaml.set("stats.crit-rate", attributes.critRate());
        yaml.set("stats.crit-damage", attributes.critDamage());
    }
}
