package com.eltena.core.application.skill;

import com.eltena.core.application.yaml.DefinitionFileLayout;
import com.eltena.core.domain.skill.SkillDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SkillLoader {

    private final JavaPlugin plugin;

    public SkillLoader(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public int loadInto(SkillCatalog catalog) {
        Map<String, SkillDefinition> loaded = new LinkedHashMap<>();
        DefinitionFileLayout.Layout layout = DefinitionFileLayout.load(plugin, "skills.yml", "skills", "skills");
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
                    "[EltenaCore] Skill filename/id mismatch: "
                        + DefinitionFileLayout.relativePath(document.file(), plugin.getDataFolder())
                        + " -> " + configuredId
                );
            }
            if (!folderIds.add(resolvedId)) {
                plugin.getLogger().warning("[EltenaCore] Duplicate skill id in folder definitions skipped: " + resolvedId);
                continue;
            }
            loaded.put(resolvedId, readSkill(resolvedId, merged));
        }

        if (layout.legacyEnabled() && layout.legacyRoot() != null) {
            for (String id : layout.legacyRoot().getKeys(false)) {
                if (loaded.containsKey(id)) {
                    plugin.getLogger().warning(
                        "[EltenaCore] Duplicate skill id detected in aggregate and folder definitions; folder definition wins: " + id
                    );
                    continue;
                }
                ConfigurationSection section = layout.legacyRoot().getConfigurationSection(id);
                if (section == null) {
                    plugin.getLogger().warning("[EltenaCore] Invalid aggregate skill section skipped: " + id);
                    continue;
                }
                loaded.put(id, readSkill(id, DefinitionFileLayout.mergeDefaults(layout.defaults(), section)));
            }
        }

        catalog.replaceAll(loaded);
        return loaded.size();
    }

    private SkillDefinition readSkill(String id, ConfigurationSection section) {
        List<String> descriptionLines = loadDescriptionLines(section, "description");
        SkillDefinition.Requirements requirements = loadRequirements(section.getConfigurationSection("requires"));
        return new SkillDefinition(
            id,
            section.getString("display-name", id),
            descriptionLines.isEmpty() ? section.getString("description", "Description not set.") : String.join(" ", descriptionLines),
            descriptionLines,
            section.getString("category", "general"),
            section.getString("icon", "minecraft:book"),
            Math.max(0, section.getInt("required-points", 0)),
            Math.max(0, section.getInt("required-level", 0)),
            Math.max(1, section.getInt("max-rank", 1)),
            loadPosition(section.getConfigurationSection("position")),
            requirements,
            loadEffects(section.getConfigurationSection("effects")),
            List.copyOf(section.getStringList("unlocks-ability")),
            requirements.flattenedPrerequisites()
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

    private SkillDefinition.Position loadPosition(ConfigurationSection section) {
        if (section == null) {
            return new SkillDefinition.Position(0, 0);
        }
        return new SkillDefinition.Position(section.getInt("x", 0), section.getInt("y", 0));
    }

    private SkillDefinition.Requirements loadRequirements(ConfigurationSection section) {
        if (section == null) {
            return SkillDefinition.Requirements.empty();
        }
        return new SkillDefinition.Requirements(
            List.copyOf(section.getStringList("all")),
            List.copyOf(section.getStringList("any")),
            loadIntMap(section.getConfigurationSection("ranks"))
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
                effects.put(effectId, raw == null ? Map.of() : Map.of("value", raw));
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (String key : effectSection.getKeys(false)) {
                values.put(key, loadValue(effectSection, key));
            }
            effects.put(effectId, values);
        }
        return effects;
    }

    private Object loadValue(ConfigurationSection section, String key) {
        ConfigurationSection nestedSection = section.getConfigurationSection(key);
        if (nestedSection == null) {
            return section.get(key);
        }
        Map<String, Object> nestedValues = new LinkedHashMap<>();
        for (String nestedKey : nestedSection.getKeys(false)) {
            nestedValues.put(nestedKey, loadValue(nestedSection, nestedKey));
        }
        return nestedValues;
    }

    private Map<String, Integer> loadIntMap(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Integer> values = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            values.put(key, Math.max(0, section.getInt(key, 0)));
        }
        return values;
    }
}
