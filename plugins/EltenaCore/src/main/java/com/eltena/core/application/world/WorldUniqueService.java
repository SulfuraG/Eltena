package com.eltena.core.application.world;

import com.eltena.core.application.yaml.DefinitionFileLayout;
import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.player.PlayerProfile;
import com.eltena.core.domain.world.UniqueRewardType;
import com.eltena.core.domain.world.WorldUniqueDefinition;
import com.eltena.core.domain.world.WorldUniqueRecord;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class WorldUniqueService {

    private static final String DEFINITIONS_PATH = "world/unique_rewards.yml";
    private static final String RECORDS_PATH = "world/world-unique.yml";

    private final ServiceRegistry services;
    private final Map<String, WorldUniqueDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, WorldUniqueRecord> records = new LinkedHashMap<>();

    public WorldUniqueService(ServiceRegistry services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    public int reload() {
        DefinitionFileLayout.load(services.plugin(), DEFINITIONS_PATH, "world-unique", "world/unique_rewards");
        ensureResource(RECORDS_PATH);
        definitions.clear();
        definitions.putAll(loadDefinitions());
        records.clear();
        records.putAll(loadRecords());
        return definitions.size();
    }

    public List<WorldUniqueDefinition> listDefinitions() {
        return List.copyOf(definitions.values());
    }

    public WorldUniqueRecord check(String id) {
        requireDefinition(id);
        return records.get(id);
    }

    public WorldUniqueGrantResult grant(UUID playerId, String playerName, String id) throws IOException {
        WorldUniqueDefinition definition = requireDefinition(id);
        WorldUniqueRecord existing = records.get(id);
        if (existing != null) {
            return new WorldUniqueGrantResult(false, existing, List.of("World-unique reward already claimed."));
        }

        PlayerProfile profile = services.playerProfiles().loadOrCreate(playerId, playerName);
        PlayerProfile updated = applyReward(profile, definition);
        WorldProgressResult rankResult = services.worldRankSystem()
            .applyExperience(updated, definition.worldRankExperience(), "World-unique achievement");
        updated = rankResult.profile();
        services.playerProfiles().save(updated);

        WorldUniqueRecord record = new WorldUniqueRecord(
            id,
            playerId,
            playerName,
            Instant.now(),
            definition.rewardType(),
            definition.rewardId()
        );
        records.put(id, record);
        saveRecords();

        List<String> messages = new ArrayList<>();
        messages.add("World-unique reward granted.");
        messages.add(definition.displayName() + " has been granted.");
        messages.addAll(rankResult.messages());
        return new WorldUniqueGrantResult(true, record, List.copyOf(messages));
    }

    private PlayerProfile applyReward(PlayerProfile profile, WorldUniqueDefinition definition) throws IOException {
        return switch (definition.rewardType()) {
            case TITLE -> services.titleSystem().grantTitleReward(profile, definition.rewardId());
            case JOB -> profile.withUnlockedJob(services.jobSystem().requireJob(definition.rewardId()).id());
            case NONE -> profile;
        };
    }

    private WorldUniqueDefinition requireDefinition(String id) {
        WorldUniqueDefinition definition = definitions.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("World-unique definition id not found: " + id);
        }
        return definition;
    }

    private Map<String, WorldUniqueDefinition> loadDefinitions() {
        var plugin = services.plugin();
        DefinitionFileLayout.Layout layout = DefinitionFileLayout.load(plugin, DEFINITIONS_PATH, "world-unique", "world/unique_rewards");
        Map<String, WorldUniqueDefinition> loaded = new LinkedHashMap<>();
        if (!layout.enabled()) {
            return loaded;
        }

        Set<String> folderIds = new java.util.LinkedHashSet<>();
        for (DefinitionFileLayout.DefinitionDocument document : DefinitionFileLayout.loadFolderDocuments(plugin, layout)) {
            ConfigurationSection merged = DefinitionFileLayout.mergeDefaults(layout.defaults(), document.yaml());
            String configuredId = merged.getString("id", "");
            String resolvedId = configuredId == null || configuredId.isBlank() ? document.fileStem() : configuredId;
            if (configuredId != null && !configuredId.isBlank() && !document.fileStem().equals(configuredId)) {
                plugin.getLogger().warning(
                    "[EltenaCore] World-unique filename/id mismatch: "
                        + DefinitionFileLayout.relativePath(document.file(), plugin.getDataFolder())
                        + " -> " + configuredId
                );
            }
            if (!folderIds.add(resolvedId)) {
                plugin.getLogger().warning("[EltenaCore] Duplicate world-unique id in folder definitions skipped: " + resolvedId);
                continue;
            }
            loaded.put(resolvedId, readDefinition(resolvedId, merged));
        }

        if (layout.legacyEnabled() && layout.legacyRoot() != null) {
            for (String id : layout.legacyRoot().getKeys(false)) {
                if (loaded.containsKey(id)) {
                    plugin.getLogger().warning(
                        "[EltenaCore] Duplicate world-unique id detected in aggregate and folder definitions; folder definition wins: " + id
                    );
                    continue;
                }
                ConfigurationSection section = layout.legacyRoot().getConfigurationSection(id);
                if (section == null) {
                    plugin.getLogger().warning("[EltenaCore] Invalid aggregate world-unique section skipped: " + id);
                    continue;
                }
                loaded.put(id, readDefinition(id, DefinitionFileLayout.mergeDefaults(layout.defaults(), section)));
            }
        }
        return loaded;
    }

    private WorldUniqueDefinition readDefinition(String id, ConfigurationSection section) {
        String displayName = section.getString("display-name", id);
        UniqueRewardType rewardType = UniqueRewardType.fromConfigValue(section.getString("reward-type", "none"));
        String rewardId = section.getString("reward-id", "");
        long worldRankExperience = section.getLong("world-rank-exp", 0L);
        return new WorldUniqueDefinition(id, displayName, rewardType, rewardId, worldRankExperience);
    }

    private Map<String, WorldUniqueRecord> loadRecords() {
        File file = DefinitionFileLayout.resolveDataFile(services.plugin(), RECORDS_PATH);
        Map<String, WorldUniqueRecord> loaded = new LinkedHashMap<>();
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file);
            ConfigurationSection section = yaml.getConfigurationSection("world-unique");
            if (section == null) {
                return loaded;
            }
            for (String id : section.getKeys(false)) {
                String path = "world-unique." + id;
                String rawUuid = yaml.getString(path + ".owner");
                if (rawUuid == null || rawUuid.isBlank()) {
                    continue;
                }
                loaded.put(id, new WorldUniqueRecord(
                    id,
                    UUID.fromString(rawUuid),
                    yaml.getString(path + ".player-name", "unknown"),
                    parseInstant(yaml.getString(path + ".timestamp")),
                    UniqueRewardType.fromConfigValue(yaml.getString(path + ".reward-type", "none")),
                    yaml.getString(path + ".reward-id", "")
                ));
            }
        } catch (IOException | InvalidConfigurationException exception) {
            services.plugin().getLogger().warning("Failed to load world-unique records: " + exception.getMessage());
        }
        return loaded;
    }

    private void saveRecords() throws IOException {
        File file = DefinitionFileLayout.resolveDataFile(services.plugin(), RECORDS_PATH);
        YamlConfiguration yaml = new YamlConfiguration();
        for (WorldUniqueRecord record : records.values()) {
            String path = "world-unique." + record.id();
            yaml.set(path + ".owner", record.playerId().toString());
            yaml.set(path + ".player-name", record.playerName());
            yaml.set(path + ".timestamp", record.timestamp().toString());
            yaml.set(path + ".reward-type", record.rewardType().configKey());
            yaml.set(path + ".reward-id", record.rewardId());
        }
        yaml.save(file);
    }

    private Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return Instant.now();
        }
        return Instant.parse(raw);
    }

    private void ensureResource(String fileName) {
        File file = DefinitionFileLayout.resolveDataFile(services.plugin(), fileName);
        if (!file.exists()) {
            services.plugin().saveResource(fileName, false);
        }
    }
}
