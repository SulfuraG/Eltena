package com.eltena.core.application.world;

import com.eltena.core.application.yaml.DefinitionFileLayout;
import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.player.PlayerProfile;
import com.eltena.core.domain.world.DiscoveryDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class DiscoveryService {

    private static final String AGGREGATE_PATH = "world/discoveries.yml";

    private final ServiceRegistry services;
    private final DiscoveryCatalog catalog;

    public DiscoveryService(ServiceRegistry services, DiscoveryCatalog catalog) {
        this.services = Objects.requireNonNull(services, "services");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public int reload() {
        catalog.replaceAll(loadDefinitions());
        return catalog.list().size();
    }

    public List<DiscoveryDefinition> listDefinitions() {
        return catalog.list();
    }

    public WorldProgressResult grant(UUID playerId, String playerName, String discoveryId) throws IOException {
        DiscoveryDefinition definition = requireDefinition(discoveryId);
        PlayerProfile profile = services.playerProfiles().loadOrCreate(playerId, playerName);
        if (profile.discoveredExplorations().contains(discoveryId)) {
            return new WorldProgressResult(profile, List.of("Discovery already recorded."));
        }

        PlayerProfile updated = profile.withDiscoveredExploration(discoveryId);
        updated = services.titleSystem().grantTitleReward(updated, definition.titleRewardId());

        List<String> messages = new ArrayList<>();
        messages.add("Discovery registered.");
        messages.add("Discovery: " + definition.displayName());

        WorldProgressResult rankResult = services.worldRankSystem()
            .applyExperience(updated, definition.worldRankExperience(), "Discovery completion");
        updated = rankResult.profile();
        messages.addAll(rankResult.messages());

        services.playerProfiles().save(updated);

        if (definition.firstDiscoveryId() != null && !definition.firstDiscoveryId().isBlank()) {
            WorldFirstGrantResult firstResult = services.worldFirstService().grant(playerId, playerName, definition.firstDiscoveryId());
            messages.addAll(firstResult.messages());
        }

        return new WorldProgressResult(updated, List.copyOf(messages));
    }

    public PlayerProfile loadProfile(UUID playerId, String playerName) throws IOException {
        return services.playerProfiles().loadOrCreate(playerId, playerName);
    }

    private DiscoveryDefinition requireDefinition(String id) {
        DiscoveryDefinition definition = catalog.find(id);
        if (definition == null) {
            throw new IllegalArgumentException("Discovery definition id not found: " + id);
        }
        return definition;
    }

    private Map<String, DiscoveryDefinition> loadDefinitions() {
        JavaPlugin plugin = services.plugin();
        DefinitionFileLayout.Layout layout = DefinitionFileLayout.load(plugin, AGGREGATE_PATH, "discoveries", "world/discoveries");
        Map<String, DiscoveryDefinition> loaded = new LinkedHashMap<>();
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
                    "[EltenaCore] Discovery filename/id mismatch: "
                        + DefinitionFileLayout.relativePath(document.file(), plugin.getDataFolder())
                        + " -> " + configuredId
                );
            }
            if (!folderIds.add(resolvedId)) {
                plugin.getLogger().warning("[EltenaCore] Duplicate discovery id in folder definitions skipped: " + resolvedId);
                continue;
            }
            loaded.put(resolvedId, readDefinition(resolvedId, merged));
        }

        if (layout.legacyEnabled() && layout.legacyRoot() != null) {
            for (String id : layout.legacyRoot().getKeys(false)) {
                if (loaded.containsKey(id)) {
                    plugin.getLogger().warning(
                        "[EltenaCore] Duplicate discovery id detected in aggregate and folder definitions; folder definition wins: " + id
                    );
                    continue;
                }
                ConfigurationSection section = layout.legacyRoot().getConfigurationSection(id);
                if (section == null) {
                    plugin.getLogger().warning("[EltenaCore] Invalid aggregate discovery section skipped: " + id);
                    continue;
                }
                loaded.put(id, readDefinition(id, DefinitionFileLayout.mergeDefaults(layout.defaults(), section)));
            }
        }
        return loaded;
    }

    private DiscoveryDefinition readDefinition(String id, ConfigurationSection section) {
        String displayName = section.getString("display-name", id);
        long worldRankExperience = section.getLong("world-rank-exp", 0L);
        String titleRewardId = section.getString("title-reward", "");
        String firstDiscoveryId = section.getString("first-discovery-id", "");
        return new DiscoveryDefinition(id, displayName, worldRankExperience, titleRewardId, firstDiscoveryId);
    }
}
