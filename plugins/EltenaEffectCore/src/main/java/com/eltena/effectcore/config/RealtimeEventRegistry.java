package com.eltena.effectcore.config;

import com.eltena.effectcore.EltenaEffectCorePlugin;
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
import org.bukkit.configuration.file.YamlConfiguration;

public final class RealtimeEventRegistry {
    private static final Set<String> IGNORED_DIRECTORY_NAMES = Set.of(
        "disabled",
        "_disabled",
        "backup",
        "_backup",
        "docs",
        "examples"
    );

    private final EltenaEffectCorePlugin plugin;
    private Map<String, YamlConfiguration> definitions = Map.of();

    public RealtimeEventRegistry(EltenaEffectCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        LoadStats stats = new LoadStats();
        LinkedHashMap<String, LoadedRealtimeEventDefinition> loadedById = new LinkedHashMap<>();

        File root = new File(plugin.getDataFolder(), "realtime-events");
        if (root.isDirectory()) {
            List<Path> files = findDefinitionFiles(root.toPath());
            stats.directoryFiles = files.size();
            for (Path filePath : files) {
                loadDefinitionFile(filePath.toFile(), loadedById, stats);
            }
        }

        if (!stats.failures.isEmpty()) {
            throw new IllegalStateException(
                "Failed to load realtime event definitions. failureCount=" + stats.failures.size()
            );
        }

        LinkedHashMap<String, YamlConfiguration> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, LoadedRealtimeEventDefinition> entry : loadedById.entrySet()) {
            resolved.put(entry.getKey(), entry.getValue().yaml());
        }
        this.definitions = Collections.unmodifiableMap(resolved);

        plugin.getLogger().info(
            "[EltenaEffectCore] realtime-events 読み込み完了: loaded="
                + this.definitions.size()
                + " directoryFiles="
                + stats.directoryFiles
                + " skipped="
                + stats.skippedCount
                + " duplicates="
                + stats.duplicateCount
        );
    }

    public int count() {
        return definitions.size();
    }

    private void loadDefinitionFile(
        File file,
        Map<String, LoadedRealtimeEventDefinition> loadedById,
        LoadStats stats
    ) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String eventId = readTrimmed(yaml.getString("id"));
        if (eventId == null) {
            stats.skippedCount++;
            if (plugin.isDebugLogEnabled()) {
                plugin.getLogger().info(
                    "[EltenaEffectCore] realtime event skipped: file="
                        + formatPath(file)
                        + " reason=missing-id"
                );
            }
            return;
        }

        LoadedRealtimeEventDefinition existing = loadedById.get(eventId);
        if (existing != null) {
            stats.duplicateCount++;
            stats.skippedCount++;
            plugin.getLogger().warning(
                "[EltenaEffectCore] Duplicate realtime event id detected: id="
                    + eventId
                    + " kept="
                    + existing.sourcePath()
                    + " ignored="
                    + formatPath(file)
            );
            return;
        }

        loadedById.put(eventId, new LoadedRealtimeEventDefinition(yaml, formatPath(file)));
    }

    private List<Path> findDefinitionFiles(Path rootPath) {
        try (Stream<Path> stream = Files.walk(rootPath)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(this::isYamlFile)
                .filter(path -> !isIgnoredRelativePath(rootPath.relativize(path)))
                .sorted(Comparator.comparing(path -> normalizePath(rootPath.relativize(path))))
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan realtime event directory: " + rootPath, exception);
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

    private record LoadedRealtimeEventDefinition(
        YamlConfiguration yaml,
        String sourcePath
    ) {
    }

    private static final class LoadStats {
        private final List<String> failures = new ArrayList<>();
        private int directoryFiles;
        private int skippedCount;
        private int duplicateCount;
    }
}
