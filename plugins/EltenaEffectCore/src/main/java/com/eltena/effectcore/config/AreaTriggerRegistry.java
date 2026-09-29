package com.eltena.effectcore.config;

import com.eltena.effectcore.EltenaEffectCorePlugin;
import com.eltena.effectcore.trigger.AreaTriggerAction;
import com.eltena.effectcore.trigger.AreaTriggerActionType;
import com.eltena.effectcore.trigger.AreaTriggerDefinition;
import com.eltena.effectcore.trigger.AreaTriggerType;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.logging.Level;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BoundingBox;

public final class AreaTriggerRegistry {
    private static final Set<String> IGNORED_DIRECTORY_NAMES = Set.of(
        "disabled",
        "_disabled",
        "backup",
        "_backup",
        "docs",
        "examples"
    );

    private final EltenaEffectCorePlugin plugin;
    private final EffectRegistry effectRegistry;
    private final EffectSequenceRegistry sequenceRegistry;
    private Map<String, AreaTriggerDefinition> triggers = Map.of();

    public AreaTriggerRegistry(
        EltenaEffectCorePlugin plugin,
        EffectRegistry effectRegistry,
        EffectSequenceRegistry sequenceRegistry
    ) {
        this.plugin = plugin;
        this.effectRegistry = effectRegistry;
        this.sequenceRegistry = sequenceRegistry;
    }

    public void load() {
        LoadStats stats = new LoadStats();
        LinkedHashMap<String, LoadedAreaTriggerDefinition> loadedById = new LinkedHashMap<>();

        loadDirectoryDefinitions(loadedById, stats);
        if (!stats.failures.isEmpty()) {
            throw new IllegalStateException(
                "Failed to load area triggers. failureCount=" + stats.failures.size()
            );
        }

        LinkedHashMap<String, AreaTriggerDefinition> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, LoadedAreaTriggerDefinition> entry : loadedById.entrySet()) {
            resolved.put(entry.getKey(), entry.getValue().definition());
        }
        this.triggers = Collections.unmodifiableMap(resolved);

        plugin.logInfo(
            "log.area-trigger-load-summary",
            Map.of(
                "loadedCount", Integer.toString(this.triggers.size()),
                "directoryFiles", Integer.toString(stats.directoryFiles),
                "skippedCount", Integer.toString(stats.skippedCount),
                "duplicateCount", Integer.toString(stats.duplicateCount)
            )
        );
    }

    public Set<String> triggerIds() {
        return triggers.keySet();
    }

    public int count() {
        return triggers.size();
    }

    public List<AreaTriggerDefinition> definitions() {
        return List.copyOf(triggers.values());
    }

    public AreaTriggerDefinition find(String triggerId) {
        return triggers.get(triggerId);
    }

    public List<AreaTriggerDefinition> matchingDefinitions(org.bukkit.Location location) {
        return triggers.values()
            .stream()
            .filter(trigger -> trigger.matches(location))
            .toList();
    }

    private void loadDirectoryDefinitions(
        Map<String, LoadedAreaTriggerDefinition> loadedById,
        LoadStats stats
    ) {
        File triggersDirectory = new File(plugin.getDataFolder(), "area-triggers");
        if (!triggersDirectory.isDirectory()) {
            return;
        }

        List<Path> triggerFiles = findTriggerFiles(triggersDirectory.toPath());
        stats.directoryFiles = triggerFiles.size();
        for (Path filePath : triggerFiles) {
            loadDefinitionFile(filePath.toFile(), loadedById, stats);
        }
    }

    private void loadDefinitionFile(
        File file,
        Map<String, LoadedAreaTriggerDefinition> loadedById,
        LoadStats stats
    ) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String triggerId = readTrimmed(yaml.getString("id"));
        if (triggerId == null) {
            warnAndSkip(
                stats,
                "log.area-trigger-skip-missing-id",
                formatPath(file),
                Map.of("file", formatPath(file))
            );
            return;
        }

        try {
            AreaTriggerDefinition definition = parseDefinition(triggerId, yaml, formatPath(file), stats);
            registerDefinition(loadedById, definition, formatPath(file), stats);
        } catch (SkippedAreaTriggerDefinitionException exception) {
            stats.skippedCount++;
        } catch (RuntimeException exception) {
            logLoadFailure(formatPath(file), exception);
            stats.failures.add(formatPath(file));
        }
    }

    private AreaTriggerDefinition parseDefinition(
        String triggerId,
        YamlConfiguration yaml,
        String sourceDescription,
        LoadStats stats
    ) {
        boolean enabled = yaml.getBoolean("enabled", true);
        String displayName = requireString(yaml, "display-name", sourceDescription);
        ConfigurationSection triggerSection = yaml.getConfigurationSection("trigger");
        if (triggerSection == null) {
            warnAndSkip(
                stats,
                "log.area-trigger-skip-invalid-trigger",
                sourceDescription,
                Map.of("file", sourceDescription, "triggerId", triggerId)
            );
        }

        AreaTriggerType triggerType = AreaTriggerType.fromConfig(
            triggerSection.getString("type"),
            sourceDescription + " -> trigger.type"
        );
        String worldName = requireString(triggerSection, "world", sourceDescription + " -> trigger");
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            warnAndSkip(
                stats,
                "log.area-trigger-skip-invalid-world",
                sourceDescription,
                Map.of("file", sourceDescription, "triggerId", triggerId, "world", worldName)
            );
        }

        ConfigurationSection minSection = triggerSection.getConfigurationSection("min");
        ConfigurationSection maxSection = triggerSection.getConfigurationSection("max");
        if (minSection == null || maxSection == null) {
            warnAndSkip(
                stats,
                "log.area-trigger-skip-invalid-box",
                sourceDescription,
                Map.of("file", sourceDescription, "triggerId", triggerId)
            );
        }

        double minX = requireDouble(minSection, "x", sourceDescription + " -> trigger.min");
        double minY = requireDouble(minSection, "y", sourceDescription + " -> trigger.min");
        double minZ = requireDouble(minSection, "z", sourceDescription + " -> trigger.min");
        double maxX = requireDouble(maxSection, "x", sourceDescription + " -> trigger.max");
        double maxY = requireDouble(maxSection, "y", sourceDescription + " -> trigger.max");
        double maxZ = requireDouble(maxSection, "z", sourceDescription + " -> trigger.max");
        if (maxX < minX || maxY < minY || maxZ < minZ) {
            warnAndSkip(
                stats,
                "log.area-trigger-skip-invalid-box",
                sourceDescription,
                Map.of("file", sourceDescription, "triggerId", triggerId)
            );
        }

        ConfigurationSection conditionsSection = yaml.getConfigurationSection("conditions");
        String permissionBypass = conditionsSection == null
            ? null
            : readTrimmed(conditionsSection.getString("permission-bypass"));
        long cooldownMs = conditionsSection == null
            ? 0L
            : requireNonNegativeLong(conditionsSection, "cooldown-ms", sourceDescription + " -> conditions");

        List<?> rawActions = yaml.getList("actions");
        if (rawActions == null || rawActions.isEmpty()) {
            warnAndSkip(
                stats,
                "log.area-trigger-skip-invalid-action",
                sourceDescription,
                Map.of("file", sourceDescription, "triggerId", triggerId)
            );
        }

        List<AreaTriggerAction> actions = new ArrayList<>();
        for (int index = 0; index < rawActions.size(); index++) {
            Object rawAction = rawActions.get(index);
            if (!(rawAction instanceof Map<?, ?>)) {
                warnAndSkip(
                    stats,
                    "log.area-trigger-skip-invalid-action",
                    sourceDescription,
                    Map.of("file", sourceDescription, "triggerId", triggerId)
                );
            }
            String actionSource = sourceDescription + " -> actions[" + index + "]";
            @SuppressWarnings("unchecked")
            Map<?, ?> actionValues = (Map<?, ?>) rawAction;
            actions.add(parseAction(triggerId, actionValues, actionSource, stats));
        }

        AreaTriggerDefinition definition = new AreaTriggerDefinition(
            triggerId,
            enabled,
            displayName,
            triggerType,
            worldName,
            new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ),
            permissionBypass,
            cooldownMs,
            List.copyOf(actions)
        );
        if (plugin.isDebugLogEnabled()) {
            plugin.logInfo(
                "log.area-trigger-loaded",
                Map.of(
                    "triggerId", definition.id(),
                    "enabled", Boolean.toString(definition.enabled()),
                    "world", definition.worldName(),
                    "actionCount", Integer.toString(definition.actions().size())
                )
            );
        }
        return definition;
    }

    private AreaTriggerAction parseAction(
        String triggerId,
        Map<?, ?> actionValues,
        String actionSource,
        LoadStats stats
    ) {
        String typeValue = readTrimmed(stringValue(actionValues.get("type")));
        String idValue = readTrimmed(stringValue(actionValues.get("id")));
        if (typeValue == null || idValue == null) {
            warnAndSkip(
                stats,
                "log.area-trigger-skip-invalid-action",
                actionSource,
                Map.of("file", actionSource, "triggerId", triggerId)
            );
        }

        AreaTriggerActionType actionType = AreaTriggerActionType.fromConfig(typeValue, actionSource);
        switch (actionType) {
            case EFFECT -> {
                if (!effectRegistry.effectIds().contains(idValue)) {
                    warnAndSkip(
                        stats,
                        "log.area-trigger-skip-invalid-action",
                        actionSource,
                        Map.of("file", actionSource, "triggerId", triggerId)
                    );
                }
            }
            case SEQUENCE -> {
                if (!sequenceRegistry.sequenceIds().contains(idValue)) {
                    warnAndSkip(
                        stats,
                        "log.area-trigger-skip-invalid-action",
                        actionSource,
                        Map.of("file", actionSource, "triggerId", triggerId)
                    );
                }
            }
        }
        return new AreaTriggerAction(actionType, idValue);
    }

    private void registerDefinition(
        Map<String, LoadedAreaTriggerDefinition> loadedById,
        AreaTriggerDefinition definition,
        String sourcePath,
        LoadStats stats
    ) {
        LoadedAreaTriggerDefinition existing = loadedById.get(definition.id());
        if (existing == null) {
            loadedById.put(definition.id(), new LoadedAreaTriggerDefinition(definition, sourcePath));
            return;
        }

        stats.duplicateCount++;
        stats.skippedCount++;
        plugin.logWarning(
            "log.area-trigger-duplicate-id",
            Map.of(
                "triggerId", definition.id(),
                "keptFile", existing.sourcePath(),
                "ignoredFile", sourcePath
            )
        );
        if (plugin.isDebugLogEnabled()) {
            plugin.logInfo(
                "log.area-trigger-skipped",
                Map.of(
                    "file", sourcePath,
                    "reason", "duplicate-id"
                )
            );
        }
    }

    private List<Path> findTriggerFiles(Path rootPath) {
        try (Stream<Path> stream = Files.walk(rootPath)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(this::isYamlFile)
                .filter(path -> !isIgnoredRelativePath(rootPath.relativize(path)))
                .sorted(Comparator.comparing(path -> normalizePath(rootPath.relativize(path))))
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Failed to scan area trigger directory: " + rootPath,
                exception
            );
        }
    }

    private boolean isYamlFile(Path path) {
        String lowerCaseName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return lowerCaseName.endsWith(".yml") || lowerCaseName.endsWith(".yaml");
    }

    private boolean isIgnoredRelativePath(Path relativePath) {
        for (Path segment : relativePath) {
            String normalized = segment.toString().trim().toLowerCase(Locale.ROOT);
            if (IGNORED_DIRECTORY_NAMES.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private void warnAndSkip(
        LoadStats stats,
        String warningKey,
        String file,
        Map<String, String> placeholders
    ) {
        plugin.logWarning(warningKey, placeholders);
        if (plugin.isDebugLogEnabled()) {
            plugin.logInfo(
                "log.area-trigger-skipped",
                Map.of(
                    "file", file,
                    "reason", warningKey
                )
            );
        }
        throw new SkippedAreaTriggerDefinitionException();
    }

    private void logLoadFailure(String sourcePath, RuntimeException exception) {
        if (exception instanceof SkippedAreaTriggerDefinitionException) {
            return;
        }
        String message = exception.getMessage() == null
            ? exception.getClass().getSimpleName()
            : exception.getMessage();
        plugin.log(
            Level.SEVERE,
            "log.area-trigger-file-error",
            Map.of(
                "file", sourcePath,
                "message", message
            ),
            exception
        );
    }

    private String requireString(ConfigurationSection section, String path, String sourceDescription) {
        String value = readTrimmed(section.getString(path));
        if (value == null) {
            throw new IllegalStateException(
                "Missing string value at " + sourceDescription + " -> " + path
            );
        }
        return value;
    }

    private String requireString(YamlConfiguration yaml, String path, String sourceDescription) {
        String value = readTrimmed(yaml.getString(path));
        if (value == null) {
            throw new IllegalStateException(
                "Missing string value at " + sourceDescription + " -> " + path
            );
        }
        return value;
    }

    private double requireDouble(
        ConfigurationSection section,
        String path,
        String sourceDescription
    ) {
        if (!section.contains(path)) {
            throw new IllegalStateException(
                "Missing numeric value at " + sourceDescription + " -> " + path
            );
        }
        return section.getDouble(path);
    }

    private long requireNonNegativeLong(
        ConfigurationSection section,
        String path,
        String sourceDescription
    ) {
        if (!section.contains(path)) {
            throw new IllegalStateException(
                "Missing numeric value at " + sourceDescription + " -> " + path
            );
        }
        long value = section.getLong(path);
        if (value < 0L) {
            throw new IllegalStateException(
                "Value must be non-negative at " + sourceDescription + " -> " + path
            );
        }
        return value;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String readTrimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String formatPath(File file) {
        return normalizePath(file.toPath());
    }

    private String normalizePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private record LoadedAreaTriggerDefinition(
        AreaTriggerDefinition definition,
        String sourcePath
    ) {
    }

    private static final class LoadStats {
        private final List<String> failures = new ArrayList<>();
        private int directoryFiles;
        private int skippedCount;
        private int duplicateCount;
    }

    private static final class SkippedAreaTriggerDefinitionException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
