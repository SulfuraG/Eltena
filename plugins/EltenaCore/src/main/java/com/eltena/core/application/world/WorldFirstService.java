package com.eltena.core.application.world;

import com.eltena.core.application.yaml.DefinitionFileLayout;
import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.player.PlayerProfile;
import com.eltena.core.domain.world.WorldFirstDefinition;
import com.eltena.core.domain.world.WorldFirstRecord;
import org.bukkit.ChatColor;
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

public final class WorldFirstService {

    private static final String DEFINITIONS_PATH = "world/world_titles.yml";
    private static final String RECORDS_PATH = "world/world-first.yml";

    private final ServiceRegistry services;
    private final Map<String, WorldFirstDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, WorldFirstRecord> records = new LinkedHashMap<>();

    public WorldFirstService(ServiceRegistry services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    public int reload() {
        DefinitionFileLayout.load(services.plugin(), DEFINITIONS_PATH, "world-first", "world/world_titles");
        ensureResource(RECORDS_PATH);
        definitions.clear();
        definitions.putAll(loadDefinitions());
        records.clear();
        records.putAll(loadRecords());
        return definitions.size();
    }

    public List<WorldFirstDefinition> listDefinitions() {
        return List.copyOf(definitions.values());
    }

    public WorldFirstRecord check(String id) {
        requireDefinition(id);
        return records.get(id);
    }

    public WorldFirstGrantResult grant(UUID playerId, String playerName, String id) throws IOException {
        WorldFirstDefinition definition = requireDefinition(id);
        WorldFirstRecord existing = records.get(id);
        if (existing != null) {
            return new WorldFirstGrantResult(false, existing, List.of("World-first reward already claimed."));
        }

        PlayerProfile profile = services.playerProfiles().loadOrCreate(playerId, playerName);
        PlayerProfile updated = services.titleSystem().grantTitleReward(profile, definition.titleRewardId());
        WorldProgressResult rankResult = services.worldRankSystem()
            .applyExperience(updated, definition.worldRankExperience(), "World-first achievement");
        updated = rankResult.profile();
        services.playerProfiles().save(updated);

        WorldFirstRecord record = new WorldFirstRecord(id, playerId, playerName, Instant.now(), definition.titleRewardId());
        records.put(id, record);
        saveRecords();

        List<String> messages = new ArrayList<>();
        messages.add("World-first achievement recorded.");
        messages.add(definition.displayName() + " awarded as a world-first achievement.");
        if (definition.titleRewardId() != null && !definition.titleRewardId().isBlank()) {
            messages.add("Title reward granted: " + services.titleSystem().displayName(definition.titleRewardId()));
        }
        messages.addAll(rankResult.messages());

        broadcastWorldFirst(record, definition);
        return new WorldFirstGrantResult(true, record, List.copyOf(messages));
    }

    private void broadcastWorldFirst(WorldFirstRecord record, WorldFirstDefinition definition) {
        String line1 = ChatColor.GOLD + record.playerName() + ChatColor.YELLOW + " achieved world first: "
            + ChatColor.AQUA + definition.displayName();
        services.plugin().getServer().broadcastMessage(line1);
        if (definition.titleRewardId() != null && !definition.titleRewardId().isBlank()) {
            String line2 = ChatColor.GREEN + "Title reward granted: "
                + services.titleSystem().displayName(definition.titleRewardId());
            services.plugin().getServer().broadcastMessage(line2);
        }
    }

    private WorldFirstDefinition requireDefinition(String id) {
        WorldFirstDefinition definition = definitions.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("World-first definition id not found: " + id);
        }
        return definition;
    }

    private Map<String, WorldFirstDefinition> loadDefinitions() {
        var plugin = services.plugin();
        DefinitionFileLayout.Layout layout = DefinitionFileLayout.load(plugin, DEFINITIONS_PATH, "world-first", "world/world_titles");
        Map<String, WorldFirstDefinition> loaded = new LinkedHashMap<>();
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
                    "[EltenaCore] World-first filename/id mismatch: "
                        + DefinitionFileLayout.relativePath(document.file(), plugin.getDataFolder())
                        + " -> " + configuredId
                );
            }
            if (!folderIds.add(resolvedId)) {
                plugin.getLogger().warning("[EltenaCore] Duplicate world-first id in folder definitions skipped: " + resolvedId);
                continue;
            }
            loaded.put(resolvedId, readDefinition(resolvedId, merged));
        }

        if (layout.legacyEnabled() && layout.legacyRoot() != null) {
            for (String id : layout.legacyRoot().getKeys(false)) {
                if (loaded.containsKey(id)) {
                    plugin.getLogger().warning(
                        "[EltenaCore] Duplicate world-first id detected in aggregate and folder definitions; folder definition wins: " + id
                    );
                    continue;
                }
                ConfigurationSection section = layout.legacyRoot().getConfigurationSection(id);
                if (section == null) {
                    plugin.getLogger().warning("[EltenaCore] Invalid aggregate world-first section skipped: " + id);
                    continue;
                }
                loaded.put(id, readDefinition(id, DefinitionFileLayout.mergeDefaults(layout.defaults(), section)));
            }
        }
        return loaded;
    }

    private WorldFirstDefinition readDefinition(String id, ConfigurationSection section) {
        String displayName = section.getString("display-name", id);
        String titleRewardId = section.getString("title-reward", "");
        long worldRankExperience = section.getLong("world-rank-exp", 0L);
        return new WorldFirstDefinition(id, displayName, titleRewardId, worldRankExperience);
    }

    private Map<String, WorldFirstRecord> loadRecords() {
        File file = DefinitionFileLayout.resolveDataFile(services.plugin(), RECORDS_PATH);
        Map<String, WorldFirstRecord> loaded = new LinkedHashMap<>();
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file);
            ConfigurationSection section = yaml.getConfigurationSection("world-first");
            if (section == null) {
                return loaded;
            }
            for (String id : section.getKeys(false)) {
                String path = "world-first." + id;
                String rawUuid = yaml.getString(path + ".first-player");
                if (rawUuid == null || rawUuid.isBlank()) {
                    continue;
                }
                loaded.put(id, new WorldFirstRecord(
                    id,
                    UUID.fromString(rawUuid),
                    yaml.getString(path + ".player-name", "unknown"),
                    parseInstant(yaml.getString(path + ".timestamp")),
                    yaml.getString(path + ".title-reward", "")
                ));
            }
        } catch (IOException | InvalidConfigurationException exception) {
            services.plugin().getLogger().warning("Failed to load world-first records: " + exception.getMessage());
        }
        return loaded;
    }

    private void saveRecords() throws IOException {
        File file = DefinitionFileLayout.resolveDataFile(services.plugin(), RECORDS_PATH);
        YamlConfiguration yaml = new YamlConfiguration();
        for (WorldFirstRecord record : records.values()) {
            String path = "world-first." + record.id();
            yaml.set(path + ".first-player", record.playerId().toString());
            yaml.set(path + ".player-name", record.playerName());
            yaml.set(path + ".timestamp", record.timestamp().toString());
            yaml.set(path + ".title-reward", record.titleRewardId());
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
