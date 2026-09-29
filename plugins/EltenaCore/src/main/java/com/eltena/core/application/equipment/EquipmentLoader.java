package com.eltena.core.application.equipment;

import com.eltena.core.application.yaml.DefinitionFileLayout;
import com.eltena.core.domain.equipment.EquipmentDefinition;
import com.eltena.core.domain.player.PlayerStatBonuses;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class EquipmentLoader {

    private final JavaPlugin plugin;

    public EquipmentLoader(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public int loadInto(EquipmentCatalog catalog) {
        Map<String, EquipmentDefinition> loaded = new LinkedHashMap<>();
        DefinitionFileLayout.Layout layout = DefinitionFileLayout.load(plugin, "equipment.yml", "equipment", "equipment");
        if (!layout.enabled()) {
            catalog.replaceAll(Map.of());
            return 0;
        }

        Set<String> folderIds = new java.util.LinkedHashSet<>();
        for (DefinitionFileLayout.DefinitionDocument document : DefinitionFileLayout.loadFolderDocuments(plugin, layout)) {
            var merged = DefinitionFileLayout.mergeDefaults(layout.defaults(), document.yaml());
            String configuredId = merged.getString("id", "");
            String resolvedId = configuredId == null || configuredId.isBlank() ? document.fileStem() : configuredId;
            if (configuredId != null && !configuredId.isBlank() && !document.fileStem().equals(configuredId)) {
                plugin.getLogger().warning(
                    "[EltenaCore] Equipment filename/id mismatch: "
                        + DefinitionFileLayout.relativePath(document.file(), plugin.getDataFolder())
                        + " -> " + configuredId
                );
            }
            if (!folderIds.add(resolvedId)) {
                plugin.getLogger().warning("[EltenaCore] Duplicate equipment id in folder definitions skipped: " + resolvedId);
                continue;
            }
            loaded.put(resolvedId, readDefinition(resolvedId, merged));
        }

        if (layout.legacyEnabled() && layout.legacyRoot() != null) {
            for (String id : layout.legacyRoot().getKeys(false)) {
                if (loaded.containsKey(id)) {
                    plugin.getLogger().warning(
                        "[EltenaCore] Duplicate equipment id detected in aggregate and folder definitions; folder definition wins: " + id
                    );
                    continue;
                }
                ConfigurationSection section = layout.legacyRoot().getConfigurationSection(id);
                if (section == null) {
                    plugin.getLogger().warning("[EltenaCore] Invalid aggregate equipment section skipped: " + id);
                    continue;
                }
                loaded.put(id, readDefinition(id, DefinitionFileLayout.mergeDefaults(layout.defaults(), section)));
            }
        }

        catalog.replaceAll(loaded);
        return loaded.size();
    }

    private EquipmentDefinition readDefinition(String id, ConfigurationSection section) {
        String itemId = section.getString("match.item-id", "");
        return new EquipmentDefinition(
            id,
            itemId,
            loadStatBonuses(section.getConfigurationSection("stats"))
        );
    }

    private PlayerStatBonuses loadStatBonuses(ConfigurationSection section) {
        PlayerStatBonuses bonuses = PlayerStatBonuses.none();
        if (section == null) {
            return bonuses;
        }
        for (String statId : section.getKeys(false)) {
            bonuses = bonuses.add(PlayerStatBonuses.single(statId, asDouble(section.get(statId))));
        }
        return bonuses;
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0.0D;
    }
}
