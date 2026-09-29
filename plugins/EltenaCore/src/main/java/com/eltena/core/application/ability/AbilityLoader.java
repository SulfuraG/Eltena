package com.eltena.core.application.ability;

import com.eltena.core.application.yaml.DefinitionFileLayout;
import com.eltena.core.domain.ability.AbilityDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class AbilityLoader {

    private final JavaPlugin plugin;

    public AbilityLoader(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public int loadInto(AbilityCatalog catalog) {
        Map<String, AbilityDefinition> loaded = new LinkedHashMap<>();
        DefinitionFileLayout.Layout layout = DefinitionFileLayout.load(plugin, "abilities.yml", "abilities", "abilities");
        if (!layout.enabled()) {
            catalog.replaceAll(Map.of());
            return 0;
        }

        Set<String> folderIds = new java.util.LinkedHashSet<>();
        for (DefinitionFileLayout.DefinitionDocument document : DefinitionFileLayout.loadFolderDocuments(plugin, layout)) {
            YamlConfiguration merged = DefinitionFileLayout.mergeDefaults(layout.defaults(), document.yaml());
            String configuredId = merged.getString("id", "");
            String resolvedId = configuredId == null || configuredId.isBlank() ? document.fileStem() : configuredId;
            if (configuredId != null && !configuredId.isBlank() && !document.fileStem().equals(configuredId)) {
                plugin.getLogger().warning(
                    "[EltenaCore] Ability filename/id mismatch: "
                        + DefinitionFileLayout.relativePath(document.file(), plugin.getDataFolder())
                        + " -> " + configuredId
                );
            }
            if (!folderIds.add(resolvedId)) {
                plugin.getLogger().warning("[EltenaCore] Duplicate ability id in folder definitions skipped: " + resolvedId);
                continue;
            }
            loaded.put(resolvedId, readAbility(resolvedId, merged));
        }

        if (layout.legacyEnabled() && layout.legacyRoot() != null) {
            for (String id : layout.legacyRoot().getKeys(false)) {
                if (loaded.containsKey(id)) {
                    plugin.getLogger().warning(
                        "[EltenaCore] Duplicate ability id detected in aggregate and folder definitions; folder definition wins: " + id
                    );
                    continue;
                }
                ConfigurationSection section = layout.legacyRoot().getConfigurationSection(id);
                if (section == null) {
                    plugin.getLogger().warning("[EltenaCore] Invalid aggregate ability section skipped: " + id);
                    continue;
                }
                loaded.put(id, readAbility(id, DefinitionFileLayout.mergeDefaults(layout.defaults(), section)));
            }
        }

        catalog.replaceAll(loaded);
        return loaded.size();
    }

    private AbilityDefinition readAbility(String id, ConfigurationSection section) {
        List<String> descriptionLines = loadDescriptionLines(section, "description");
        AbilityDefinition.Cost cost = loadCost(section.getConfigurationSection("cost"));
        AbilityDefinition.Requirements requirements = loadRequirements(section.getConfigurationSection("requires"));
        AbilityDefinition.Executor executor = loadExecutor(section.getConfigurationSection("executor"));
        return new AbilityDefinition(
            id,
            section.getString("display-name", id),
            descriptionLines.isEmpty() ? section.getString("description", "Description not set.") : String.join(" ", descriptionLines),
            descriptionLines,
            section.getString("category", "general"),
            section.getString("icon", "minecraft:book"),
            Math.max(0, section.getInt("cooldown", 0)),
            cost,
            loadWeaponTypes(section, "weapon-types"),
            requirements,
            loadEffects(section.getConfigurationSection("effects")),
            executor
        );
    }

    private List<String> loadDescriptionLines(ConfigurationSection section, String path) {
        List<String> lines = section.getStringList(path);
        if (!lines.isEmpty()) {
            return List.copyOf(lines);
        }
        String single = section.getString(path, "");
        return single == null || single.isBlank() ? List.of() : List.of(single);
    }

    private AbilityDefinition.Cost loadCost(ConfigurationSection section) {
        if (section == null) {
            return AbilityDefinition.Cost.empty();
        }
        return new AbilityDefinition.Cost(Math.max(0, section.getInt("mp", 0)));
    }

    private AbilityDefinition.Requirements loadRequirements(ConfigurationSection section) {
        if (section == null) {
            return AbilityDefinition.Requirements.empty();
        }
        return new AbilityDefinition.Requirements(List.copyOf(section.getStringList("skills")));
    }

    private List<String> loadWeaponTypes(ConfigurationSection section, String path) {
        List<String> configured = section.getStringList(path);
        if (configured.isEmpty()) {
            String single = section.getString(path, "");
            configured = single == null || single.isBlank() ? List.of("any") : List.of(single);
        }
        List<String> normalized = configured.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .distinct()
            .collect(Collectors.toList());
        return normalized.isEmpty() ? List.of("any") : List.copyOf(normalized);
    }

    private AbilityDefinition.Executor loadExecutor(ConfigurationSection section) {
        if (section == null) {
            return AbilityDefinition.Executor.empty();
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            if ("type".equalsIgnoreCase(key) || "skill".equalsIgnoreCase(key) || "action".equalsIgnoreCase(key)) {
                continue;
            }
            meta.put(key, normalizeConfigValue(section.get(key)));
        }
        return new AbilityDefinition.Executor(
            section.getString("type", ""),
            section.getString("skill", ""),
            section.getString("action", ""),
            meta
        );
    }

    private Map<String, Map<String, Object>> loadEffects(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Map<String, Object>> effects = new LinkedHashMap<>();
        for (String effectId : section.getKeys(false)) {
            ConfigurationSection effectSection = section.getConfigurationSection(effectId);
            if (effectSection == null) {
                Object raw = section.get(effectId);
                effects.put(effectId, raw == null ? Map.of() : Map.of("value", normalizeConfigValue(raw)));
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (String key : effectSection.getKeys(false)) {
                values.put(key, normalizeConfigValue(effectSection.get(key)));
            }
            effects.put(effectId, values);
        }
        return effects;
    }

    private Object normalizeConfigValue(Object value) {
        if (value instanceof ConfigurationSection nestedSection) {
            Map<String, Object> nested = new LinkedHashMap<>();
            for (String key : nestedSection.getKeys(false)) {
                nested.put(key, normalizeConfigValue(nestedSection.get(key)));
            }
            return nested;
        }
        if (value instanceof List<?> list) {
            return list.stream()
                .map(this::normalizeConfigValue)
                .toList();
        }
        return value;
    }
}
