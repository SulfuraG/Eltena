package com.eltena.soundcore.config;

import com.eltena.soundcore.EltenaSoundCorePlugin;
import com.eltena.soundcore.sound.SoundCategory;
import com.eltena.soundcore.sound.SoundDefinition;
import com.eltena.soundcore.sound.SoundType;
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

public final class SoundRegistry {
    private static final Set<String> IGNORED_DIRECTORY_NAMES = Set.of(
        "disabled",
        "_disabled",
        "backup",
        "_backup",
        "docs",
        "examples"
    );

    private final EltenaSoundCorePlugin plugin;
    private Map<String, SoundDefinition> sounds = Map.of();

    public SoundRegistry(EltenaSoundCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        LoadStats stats = new LoadStats();
        LinkedHashMap<String, LoadedSoundDefinition> loadedById = new LinkedHashMap<>();
        loadDirectoryDefinitions(loadedById, stats);

        if (!stats.failures.isEmpty()) {
            throw new IllegalStateException(
                "Failed to load sound definitions. failureCount=" + stats.failures.size()
            );
        }

        LinkedHashMap<String, SoundDefinition> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, LoadedSoundDefinition> entry : loadedById.entrySet()) {
            resolved.put(entry.getKey(), entry.getValue().definition());
        }
        this.sounds = Collections.unmodifiableMap(resolved);

        plugin.logInfo(
            "log.sound-load-summary",
            Map.of(
                "loadedCount", Integer.toString(this.sounds.size()),
                "directoryFiles", Integer.toString(stats.directoryFiles),
                "skippedCount", Integer.toString(stats.skippedCount),
                "duplicateCount", Integer.toString(stats.duplicateCount)
            )
        );
    }

    public SoundDefinition require(String soundId) {
        SoundDefinition definition = sounds.get(soundId);
        if (definition == null) {
            throw new IllegalArgumentException(soundId);
        }
        return definition;
    }

    public Set<String> soundIds() {
        return sounds.keySet();
    }

    public int count() {
        return sounds.size();
    }

    private void loadDirectoryDefinitions(
        Map<String, LoadedSoundDefinition> loadedById,
        LoadStats stats
    ) {
        File soundsDirectory = new File(plugin.getDataFolder(), "sounds");
        if (!soundsDirectory.isDirectory()) {
            return;
        }

        List<Path> soundFiles = findSoundFiles(soundsDirectory.toPath());
        stats.directoryFiles = soundFiles.size();
        for (Path filePath : soundFiles) {
            loadDefinitionFile(filePath.toFile(), loadedById, stats);
        }
    }

    private void loadDefinitionFile(
        File file,
        Map<String, LoadedSoundDefinition> loadedById,
        LoadStats stats
    ) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String soundId = readTrimmed(yaml.getString("id"));
        if (soundId == null) {
            plugin.logWarning("log.sound-skip-missing-id", Map.of("file", formatPath(file)));
            stats.skippedCount++;
            return;
        }

        String type = readTrimmed(yaml.getString("type"));
        if (type == null) {
            plugin.logWarning(
                "log.sound-skip-missing-type",
                Map.of("file", formatPath(file), "soundId", soundId)
            );
            stats.skippedCount++;
            return;
        }

        try {
            SoundDefinition definition = parseDefinition(soundId, type, yaml, formatPath(file));
            registerDefinition(loadedById, definition, formatPath(file), stats);
        } catch (SkippedSoundDefinitionException exception) {
            stats.skippedCount++;
        } catch (RuntimeException exception) {
            logLoadFailure(formatPath(file), exception);
            stats.failures.add(formatPath(file));
        }
    }

    private SoundDefinition parseDefinition(
        String soundId,
        String typeValue,
        YamlConfiguration yaml,
        String sourceDescription
    ) {
        SoundType type;
        try {
            type = SoundType.fromConfig(typeValue);
        } catch (IllegalArgumentException exception) {
            plugin.logWarning(
                "log.sound-skip-unsupported-type",
                Map.of(
                    "file", sourceDescription,
                    "soundId", soundId,
                    "type", typeValue
                )
            );
            throw new SkippedSoundDefinitionException();
        }

        String categoryValue = requireString(yaml, "category", sourceDescription);
        SoundCategory category;
        try {
            category = SoundCategory.fromConfig(categoryValue);
        } catch (IllegalArgumentException exception) {
            plugin.logWarning(
                "log.sound-skip-unsupported-category",
                Map.of(
                    "file", sourceDescription,
                    "soundId", soundId,
                    "category", categoryValue
                )
            );
            throw new SkippedSoundDefinitionException();
        }

        return new SoundDefinition(
            soundId,
            type,
            requireString(yaml, "display-name", sourceDescription),
            requireString(yaml, "sound-event", sourceDescription),
            category,
            yaml.getBoolean("loop", false),
            requirePositiveDouble(yaml, "volume", sourceDescription),
            requirePositiveDouble(yaml, "pitch", sourceDescription),
            readNonNegativeLong(yaml, "duration-ms", 0L, sourceDescription),
            readNonNegativeLong(yaml, "fade-in-ms", 0L, sourceDescription),
            readNonNegativeLong(yaml, "fade-out-ms", 0L, sourceDescription)
        );
    }

    private void registerDefinition(
        Map<String, LoadedSoundDefinition> loadedById,
        SoundDefinition definition,
        String sourcePath,
        LoadStats stats
    ) {
        LoadedSoundDefinition existing = loadedById.get(definition.id());
        if (existing == null) {
            loadedById.put(definition.id(), new LoadedSoundDefinition(definition, sourcePath));
            return;
        }

        stats.duplicateCount++;
        stats.skippedCount++;
        plugin.logWarning(
            "log.sound-duplicate-id",
            Map.of(
                "soundId", definition.id(),
                "keptFile", existing.sourcePath(),
                "ignoredFile", sourcePath
            )
        );
    }

    private List<Path> findSoundFiles(Path rootPath) {
        try (Stream<Path> stream = Files.walk(rootPath)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(this::isYamlFile)
                .filter(path -> !isIgnoredRelativePath(rootPath.relativize(path)))
                .sorted(Comparator.comparing(path -> normalizePath(rootPath.relativize(path))))
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan sound directory: " + rootPath, exception);
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

    private void logLoadFailure(String sourcePath, RuntimeException exception) {
        if (exception instanceof SkippedSoundDefinitionException) {
            return;
        }
        String message = exception.getMessage() == null
            ? exception.getClass().getSimpleName()
            : exception.getMessage();
        plugin.log(
            Level.SEVERE,
            "log.sound-file-error",
            Map.of(
                "file", sourcePath,
                "message", message
            ),
            exception
        );
    }

    private String requireString(YamlConfiguration yaml, String path, String sourceDescription) {
        String value = readTrimmed(yaml.getString(path));
        if (value == null) {
            throw new IllegalStateException("Missing string value at " + sourceDescription + " -> " + path);
        }
        return value;
    }

    private double requirePositiveDouble(YamlConfiguration yaml, String path, String sourceDescription) {
        if (!yaml.contains(path)) {
            throw new IllegalStateException("Missing numeric value at " + sourceDescription + " -> " + path);
        }
        double value = yaml.getDouble(path);
        if (value <= 0.0D) {
            throw new IllegalStateException("Value must be positive at " + sourceDescription + " -> " + path);
        }
        return value;
    }

    private long readNonNegativeLong(
        YamlConfiguration yaml,
        String path,
        long fallback,
        String sourceDescription
    ) {
        if (!yaml.contains(path)) {
            return fallback;
        }
        long value = yaml.getLong(path);
        if (value < 0L) {
            throw new IllegalStateException("Value must be non-negative at " + sourceDescription + " -> " + path);
        }
        return value;
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

    private record LoadedSoundDefinition(
        SoundDefinition definition,
        String sourcePath
    ) {
    }

    private static final class LoadStats {
        private final List<String> failures = new ArrayList<>();
        private int directoryFiles;
        private int skippedCount;
        private int duplicateCount;
    }

    private static final class SkippedSoundDefinitionException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
