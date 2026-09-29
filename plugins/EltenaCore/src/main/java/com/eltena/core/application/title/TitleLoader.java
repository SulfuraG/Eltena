package com.eltena.core.application.title;

import com.eltena.core.application.yaml.DefinitionFileLayout;
import com.eltena.core.domain.title.TitleCategory;
import com.eltena.core.domain.title.TitleDefinition;
import com.eltena.core.i18n.MessageService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TitleLoader {

    private static final String AGGREGATE_PATH = "world/titles.yml";

    private final JavaPlugin plugin;
    private final MessageService messages;

    public TitleLoader(JavaPlugin plugin, MessageService messages) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public File ensureTitlesFileExists() {
        return DefinitionFileLayout.resolveDataFile(plugin, AGGREGATE_PATH);
    }

    public int loadInto(TitleCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");

        File file = ensureTitlesFileExists();
        warnDuplicateIds(file);

        Map<String, TitleDefinition> definitions = loadDefinitions();
        if (definitions.isEmpty()) {
            plugin.getLogger().warning(msg("title-loader.warning.empty-definitions"));
            definitions = fallbackDefinitions();
        }

        if (!definitions.containsKey("none")) {
            plugin.getLogger().warning(msg("title-loader.warning.missing-none"));
            definitions.put("none", fallbackDefinitions().get("none"));
        }

        catalog.replaceAll(definitions);
        return definitions.size();
    }

    public File titlesFile() {
        return ensureTitlesFileExists();
    }

    private Map<String, TitleDefinition> loadDefinitions() {
        DefinitionFileLayout.Layout layout = DefinitionFileLayout.load(plugin, AGGREGATE_PATH, "titles", "world/titles");
        if (!layout.enabled()) {
            return Map.of();
        }

        Map<String, TitleDefinition> definitions = new LinkedHashMap<>();
        Set<String> folderIds = new java.util.LinkedHashSet<>();

        for (DefinitionFileLayout.DefinitionDocument document : DefinitionFileLayout.loadFolderDocuments(plugin, layout)) {
            var merged = DefinitionFileLayout.mergeDefaults(layout.defaults(), document.yaml());
            String configuredId = merged.getString("id", "");
            String resolvedId = configuredId == null || configuredId.isBlank() ? document.fileStem() : configuredId;
            if (configuredId != null && !configuredId.isBlank() && !document.fileStem().equals(configuredId)) {
                plugin.getLogger().warning(
                    "[EltenaCore] Title filename/id mismatch: "
                        + DefinitionFileLayout.relativePath(document.file(), plugin.getDataFolder())
                        + " -> " + configuredId
                );
            }
            if (!folderIds.add(resolvedId)) {
                plugin.getLogger().warning(msg("title-loader.warning.duplicate-id", "id", resolvedId));
                continue;
            }
            definitions.put(resolvedId, readDefinition(resolvedId, merged));
        }

        if (layout.legacyEnabled() && layout.legacyRoot() != null) {
            for (String titleId : layout.legacyRoot().getKeys(false)) {
                if (definitions.containsKey(titleId)) {
                    plugin.getLogger().warning(msg("title-loader.warning.duplicate-id", "id", titleId));
                    continue;
                }
                ConfigurationSection section = layout.legacyRoot().getConfigurationSection(titleId);
                if (section == null) {
                    plugin.getLogger().warning(msg("title-loader.warning.load-failed", "message", "invalid section: " + titleId));
                    continue;
                }
                definitions.put(titleId, readDefinition(titleId, DefinitionFileLayout.mergeDefaults(layout.defaults(), section)));
            }
        }
        return definitions;
    }

    private TitleDefinition readDefinition(String titleId, ConfigurationSection section) {
        String displayName = section.getString("display-name");
        String description = section.getString("description");
        String categoryRaw = section.getString("category", "honor");

        if (displayName == null || displayName.isBlank()) {
            plugin.getLogger().warning(msg("title-loader.warning.missing-display-name", "id", titleId));
            displayName = titleId;
        }

        if (description == null || description.isBlank()) {
            plugin.getLogger().warning(msg("title-loader.warning.missing-description", "id", titleId));
            description = msg("title-loader.default.description");
        }

        TitleCategory category = TitleCategory.fromConfigValue(categoryRaw);
        if (!category.configKey().equalsIgnoreCase(categoryRaw == null ? "" : categoryRaw.trim())) {
            plugin.getLogger().warning(msg("title-loader.warning.invalid-category", "id", titleId, "value", categoryRaw));
        }

        return new TitleDefinition(
            titleId,
            displayName,
            description,
            category,
            loadEffects(section.getConfigurationSection("effects"))
        );
    }

    private void warnDuplicateIds(File file) {
        try {
            if (!file.exists()) {
                return;
            }
            List<String> lines = Files.readAllLines(file.toPath());
            Map<String, Integer> seen = new LinkedHashMap<>();
            boolean inTitles = false;
            for (String line : lines) {
                if (line.startsWith("titles:")) {
                    inTitles = true;
                    continue;
                }
                if (!inTitles) {
                    continue;
                }
                if (!line.startsWith("  ")) {
                    inTitles = false;
                    continue;
                }
                if (line.startsWith("  ") && !line.startsWith("    ") && line.trim().endsWith(":")) {
                    String id = line.trim().substring(0, line.trim().length() - 1);
                    int count = seen.getOrDefault(id, 0) + 1;
                    seen.put(id, count);
                    if (count > 1) {
                        plugin.getLogger().warning(msg("title-loader.warning.duplicate-id", "id", id));
                    }
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().warning(msg("title-loader.warning.duplicate-check-failed", "message", exception.getMessage()));
        }
    }

    private Map<String, TitleDefinition> fallbackDefinitions() {
        Map<String, TitleDefinition> defaults = new LinkedHashMap<>();
        defaults.put(
            "none",
            new TitleDefinition(
                "none",
                msg("title-loader.fallback.none.name"),
                msg("title-loader.fallback.none.description"),
                TitleCategory.HONOR,
                Map.of()
            )
        );
        defaults.put(
            "first_step",
            new TitleDefinition(
                "first_step",
                msg("title-loader.fallback.first-step.name"),
                msg("title-loader.fallback.first-step.description"),
                TitleCategory.ACHIEVEMENT,
                Map.of()
            )
        );
        defaults.put(
            "explorer",
            new TitleDefinition(
                "explorer",
                msg("title-loader.fallback.explorer.name"),
                msg("title-loader.fallback.explorer.description"),
                TitleCategory.EXPLORATION,
                Map.of()
            )
        );
        return defaults;
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

    private String msg(String key, Object... pairs) {
        return messages.get(key, pairs);
    }
}
