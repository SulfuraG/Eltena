package com.eltena.effectcore.config;

import com.eltena.effectcore.EltenaEffectCorePlugin;
import com.eltena.effectcore.sequence.SequenceDefinition;
import com.eltena.effectcore.sequence.SequenceStep;
import com.eltena.effectcore.sequence.SequenceStepType;
import com.eltena.effectcore.sequence.SyncDurationMode;
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
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;

public final class EffectSequenceRegistry {
    private static final Set<String> IGNORED_DIRECTORY_NAMES = Set.of(
        "disabled",
        "_disabled",
        "backup",
        "_backup",
        "docs",
        "examples"
    );

    private final EltenaEffectCorePlugin plugin;
    private Map<String, SequenceDefinition> sequences = Map.of();

    public EffectSequenceRegistry(EltenaEffectCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        LoadStats stats = new LoadStats();
        LinkedHashMap<String, LoadedSequenceDefinition> loadedById = new LinkedHashMap<>();

        loadDirectoryDefinitions(loadedById, stats);
        if (!stats.failures.isEmpty()) {
            throw new IllegalStateException(
                "Failed to load effect sequences. failureCount=" + stats.failures.size()
            );
        }

        LinkedHashMap<String, SequenceDefinition> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, LoadedSequenceDefinition> entry : loadedById.entrySet()) {
            resolved.put(entry.getKey(), entry.getValue().definition());
        }
        this.sequences = Collections.unmodifiableMap(resolved);

        plugin.logInfo(
            "log.sequence-load-summary",
            Map.of(
                "loadedCount", Integer.toString(this.sequences.size()),
                "directoryFiles", Integer.toString(stats.directoryFiles),
                "skippedCount", Integer.toString(stats.skippedCount),
                "duplicateCount", Integer.toString(stats.duplicateCount)
            )
        );
    }

    public SequenceDefinition require(String sequenceId) {
        SequenceDefinition definition = sequences.get(sequenceId);
        if (definition == null) {
            throw new IllegalArgumentException(sequenceId);
        }
        return definition;
    }

    public Set<String> sequenceIds() {
        return sequences.keySet();
    }

    public int count() {
        return sequences.size();
    }

    private void loadDirectoryDefinitions(
        Map<String, LoadedSequenceDefinition> loadedById,
        LoadStats stats
    ) {
        File sequencesDirectory = new File(plugin.getDataFolder(), "effect-sequences");
        if (!sequencesDirectory.isDirectory()) {
            return;
        }

        List<Path> sequenceFiles = findSequenceFiles(sequencesDirectory.toPath());
        stats.directoryFiles = sequenceFiles.size();
        for (Path filePath : sequenceFiles) {
            loadDefinitionFile(filePath.toFile(), loadedById, stats);
        }
    }

    private void loadDefinitionFile(
        File file,
        Map<String, LoadedSequenceDefinition> loadedById,
        LoadStats stats
    ) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String sequenceId = readTrimmed(yaml.getString("id"));
        if (sequenceId == null) {
            plugin.logWarning(
                "log.sequence-skip-missing-id",
                Map.of("file", formatPath(file))
            );
            stats.skippedCount++;
            return;
        }

        try {
            SequenceDefinition definition = parseDefinition(sequenceId, yaml, formatPath(file));
            registerDefinition(loadedById, definition, formatPath(file), stats);
        } catch (SkippedSequenceDefinitionException exception) {
            stats.skippedCount++;
        } catch (RuntimeException exception) {
            logLoadFailure(formatPath(file), exception);
            stats.failures.add(formatPath(file));
        }
    }

    private SequenceDefinition parseDefinition(
        String sequenceId,
        YamlConfiguration yaml,
        String sourceDescription
    ) {
        String displayName = requireString(yaml, "display-name", sourceDescription);
        List<?> rawSteps = yaml.getList("steps");
        if (rawSteps == null || rawSteps.isEmpty()) {
            plugin.logWarning(
                "log.sequence-skip-missing-steps",
                Map.of(
                    "file", sourceDescription,
                    "sequenceId", sequenceId
                )
            );
            throw new SkippedSequenceDefinitionException();
        }

        List<SequenceStep> steps = new ArrayList<>();
        long previousDelayMs = -1L;
        Set<String> aliases = new HashSet<>();
        for (int index = 0; index < rawSteps.size(); index++) {
            Object rawStep = rawSteps.get(index);
            String stepSource = sourceDescription + " -> steps[" + index + "]";
            if (!(rawStep instanceof Map<?, ?> stepValues)) {
                throw new IllegalStateException("Sequence step must be a map at " + stepSource);
            }

            long delayMs = requireNonNegativeLong(stepValues, "delay-ms", stepSource);
            if (delayMs < previousDelayMs) {
                throw new IllegalStateException(
                    "Sequence step delay must be non-decreasing at " + stepSource
                );
            }
            previousDelayMs = delayMs;
            SequenceStep step = parseStep(stepValues, stepSource, delayMs);
            if (step.alias() != null) {
                String aliasKey = normalizeAlias(step.alias());
                if (!aliases.add(aliasKey)) {
                    throw new IllegalStateException(
                        "Duplicate sequence alias at " + stepSource + " -> " + step.alias()
                    );
                }
            }
            steps.add(step);
        }

        return new SequenceDefinition(
            sequenceId,
            displayName,
            Collections.unmodifiableList(steps)
        );
    }

    private SequenceStep parseStep(Map<?, ?> stepValues, String stepSource, long delayMs) {
        String effectId = readTrimmed(stringValue(stepValues.get("effect")));
        String soundId = readTrimmed(stringValue(stepValues.get("sound")));
        String liveSoundId = readTrimmed(stringValue(stepValues.get("live-sound")));
        String explicitType = readTrimmed(stringValue(stepValues.get("type")));
        String explicitId = readTrimmed(stringValue(stepValues.get("id")));
        String alias = readTrimmed(stringValue(stepValues.get("alias")));
        String syncDurationWith = readTrimmed(stringValue(stepValues.get("sync-duration-with")));

        int shorthandCount = countDefined(effectId, soundId, liveSoundId);
        if (shorthandCount > 1) {
            throw new IllegalStateException(
                "Sequence step cannot define effect, sound, and live-sound together at " + stepSource
            );
        }
        if (shorthandCount > 0 && (explicitType != null || explicitId != null)) {
            throw new IllegalStateException(
                "Sequence step cannot mix shorthand keys with type/id at " + stepSource
            );
        }
        if (effectId != null) {
            EffectSyncOptions syncOptions = parseEffectSyncOptions(
                stepValues,
                stepSource,
                alias,
                syncDurationWith
            );
            return SequenceStep.effect(
                effectId,
                delayMs,
                syncOptions.syncDurationWith(),
                syncOptions.syncDurationMode(),
                syncOptions.syncDurationScale(),
                syncOptions.syncDurationMinMs(),
                syncOptions.syncDurationMaxMs()
            );
        }
        if (soundId != null) {
            validateSoundLikeStep(stepValues, stepSource, syncDurationWith);
            return SequenceStep.sound(soundId, delayMs, alias);
        }
        if (liveSoundId != null) {
            validateSoundLikeStep(stepValues, stepSource, syncDurationWith);
            return SequenceStep.liveSound(liveSoundId, delayMs, alias);
        }
        if (explicitType != null || explicitId != null) {
            if (explicitType == null || explicitId == null) {
                throw new IllegalStateException(
                    "Sequence step type/id must both be present at " + stepSource
                );
            }
            SequenceStepType stepType = SequenceStepType.fromConfig(explicitType);
            return switch (stepType) {
                case EFFECT -> {
                    EffectSyncOptions syncOptions = parseEffectSyncOptions(
                        stepValues,
                        stepSource,
                        alias,
                        syncDurationWith
                    );
                    yield SequenceStep.effect(
                        explicitId,
                        delayMs,
                        syncOptions.syncDurationWith(),
                        syncOptions.syncDurationMode(),
                        syncOptions.syncDurationScale(),
                        syncOptions.syncDurationMinMs(),
                        syncOptions.syncDurationMaxMs()
                    );
                }
                case SOUND -> {
                    validateSoundLikeStep(stepValues, stepSource, syncDurationWith);
                    yield SequenceStep.sound(explicitId, delayMs, alias);
                }
                case LIVE_SOUND -> {
                    validateSoundLikeStep(stepValues, stepSource, syncDurationWith);
                    yield SequenceStep.liveSound(explicitId, delayMs, alias);
                }
                case CAMERA -> {
                    validateCameraStep(stepValues, stepSource, alias, syncDurationWith);
                    yield SequenceStep.camera(explicitId, delayMs);
                }
            };
        }

        throw new IllegalStateException(
            "Sequence step must define effect, sound, live-sound, or type/id at " + stepSource
        );
    }

    private EffectSyncOptions parseEffectSyncOptions(
        Map<?, ?> stepValues,
        String stepSource,
        String alias,
        String syncDurationWith
    ) {
        if (alias != null) {
            throw new IllegalStateException(
                "Sequence effect step cannot define alias at " + stepSource
            );
        }
        SyncDurationMode syncDurationMode = parseSyncDurationMode(stepValues, stepSource);
        double syncDurationScale = readPositiveDouble(
            stepValues,
            "sync-duration-scale",
            1.0D,
            stepSource
        );
        long syncDurationMinMs = readNonNegativeLong(
            stepValues,
            "sync-duration-min-ms",
            0L,
            stepSource
        );
        long syncDurationMaxMs = readNonNegativeLong(
            stepValues,
            "sync-duration-max-ms",
            Long.MAX_VALUE,
            stepSource
        );
        boolean hasSyncDurationWith = syncDurationWith != null;
        boolean hasOtherSyncFields = stepValues.containsKey("sync-duration-mode")
            || stepValues.containsKey("sync-duration-scale")
            || stepValues.containsKey("sync-duration-min-ms")
            || stepValues.containsKey("sync-duration-max-ms");
        if (!hasSyncDurationWith && hasOtherSyncFields) {
            throw new IllegalStateException(
                "Sequence effect step requires sync-duration-with when sync-duration fields are present at "
                    + stepSource
            );
        }
        if (hasSyncDurationWith) {
            if (syncDurationScale <= 0.0D) {
                throw new IllegalStateException(
                    "sync-duration-scale must be positive at " + stepSource
                );
            }
            if (syncDurationMaxMs < syncDurationMinMs) {
                throw new IllegalStateException(
                    "sync-duration-max-ms must be >= sync-duration-min-ms at " + stepSource
                );
            }
        }
        return new EffectSyncOptions(
            syncDurationWith,
            syncDurationMode,
            syncDurationScale,
            syncDurationMinMs,
            syncDurationMaxMs
        );
    }

    private void validateSoundLikeStep(
        Map<?, ?> stepValues,
        String stepSource,
        String syncDurationWith
    ) {
        if (syncDurationWith != null
            || stepValues.containsKey("sync-duration-mode")
            || stepValues.containsKey("sync-duration-scale")
            || stepValues.containsKey("sync-duration-min-ms")
            || stepValues.containsKey("sync-duration-max-ms")) {
            throw new IllegalStateException(
                "Sequence sound/live-sound step cannot define sync-duration fields at " + stepSource
            );
        }
    }

    private void validateCameraStep(
        Map<?, ?> stepValues,
        String stepSource,
        String alias,
        String syncDurationWith
    ) {
        if (alias != null) {
            throw new IllegalStateException(
                "Sequence camera step does not support alias at " + stepSource
            );
        }
        if (syncDurationWith != null
            || stepValues.containsKey("sync-duration-mode")
            || stepValues.containsKey("sync-duration-scale")
            || stepValues.containsKey("sync-duration-min-ms")
            || stepValues.containsKey("sync-duration-max-ms")) {
            throw new IllegalStateException(
                "Sequence camera step does not support sync-duration at " + stepSource
            );
        }
    }

    private SyncDurationMode parseSyncDurationMode(Map<?, ?> values, String stepSource) {
        String configured = readTrimmed(stringValue(values.get("sync-duration-mode")));
        if (configured == null) {
            return SyncDurationMode.MATCH;
        }
        try {
            return SyncDurationMode.fromConfig(configured);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                "Invalid sync-duration-mode at " + stepSource,
                exception
            );
        }
    }

    private double readPositiveDouble(
        Map<?, ?> values,
        String key,
        double fallback,
        String sourceDescription
    ) {
        Object rawValue = values.get(key);
        if (rawValue == null) {
            return fallback;
        }

        double value;
        if (rawValue instanceof Number number) {
            value = number.doubleValue();
        } else {
            String rawText = readTrimmed(rawValue.toString());
            if (rawText == null) {
                return fallback;
            }
            try {
                value = Double.parseDouble(rawText);
            } catch (NumberFormatException exception) {
                throw new IllegalStateException(
                    "Invalid numeric value at " + sourceDescription + " -> " + key,
                    exception
                );
            }
        }

        if (value <= 0.0D) {
            throw new IllegalStateException(
                "Value must be positive at " + sourceDescription + " -> " + key
            );
        }
        return value;
    }

    private long readNonNegativeLong(
        Map<?, ?> values,
        String key,
        long fallback,
        String sourceDescription
    ) {
        Object rawValue = values.get(key);
        if (rawValue == null) {
            return fallback;
        }

        long value;
        if (rawValue instanceof Number number) {
            value = number.longValue();
        } else {
            String rawText = readTrimmed(rawValue.toString());
            if (rawText == null) {
                return fallback;
            }
            try {
                value = Long.parseLong(rawText);
            } catch (NumberFormatException exception) {
                throw new IllegalStateException(
                    "Invalid numeric value at " + sourceDescription + " -> " + key,
                    exception
                );
            }
        }

        if (value < 0L) {
            throw new IllegalStateException(
                "Value must be non-negative at " + sourceDescription + " -> " + key
            );
        }
        return value;
    }

    private int countDefined(String... values) {
        int count = 0;
        for (String value : values) {
            if (value != null) {
                count++;
            }
        }
        return count;
    }

    private String normalizeAlias(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private void registerDefinition(
        Map<String, LoadedSequenceDefinition> loadedById,
        SequenceDefinition definition,
        String sourcePath,
        LoadStats stats
    ) {
        LoadedSequenceDefinition existing = loadedById.get(definition.id());
        if (existing == null) {
            loadedById.put(definition.id(), new LoadedSequenceDefinition(definition, sourcePath));
            return;
        }

        stats.duplicateCount++;
        stats.skippedCount++;
        plugin.logWarning(
            "log.sequence-duplicate-id",
            Map.of(
                "sequenceId", definition.id(),
                "keptFile", existing.sourcePath(),
                "ignoredFile", sourcePath
            )
        );
    }

    private List<Path> findSequenceFiles(Path rootPath) {
        try (Stream<Path> stream = Files.walk(rootPath)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(this::isYamlFile)
                .filter(path -> !isIgnoredRelativePath(rootPath.relativize(path)))
                .sorted(Comparator.comparing(path -> normalizePath(rootPath.relativize(path))))
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Failed to scan sequence directory: " + rootPath,
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

    private void logLoadFailure(String sourcePath, RuntimeException exception) {
        if (exception instanceof SkippedSequenceDefinitionException) {
            return;
        }
        String message = exception.getMessage() == null
            ? exception.getClass().getSimpleName()
            : exception.getMessage();
        plugin.log(
            Level.SEVERE,
            "log.sequence-file-error",
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
            throw new IllegalStateException(
                "Missing string value at " + sourceDescription + " -> " + path
            );
        }
        return value;
    }

    private long requireNonNegativeLong(
        Map<?, ?> values,
        String key,
        String sourceDescription
    ) {
        Object rawValue = values.get(key);
        if (rawValue == null) {
            throw new IllegalStateException(
                "Missing numeric value at " + sourceDescription + " -> " + key
            );
        }

        long value;
        if (rawValue instanceof Number number) {
            value = number.longValue();
        } else {
            String rawText = readTrimmed(rawValue.toString());
            if (rawText == null) {
                throw new IllegalStateException(
                    "Missing numeric value at " + sourceDescription + " -> " + key
                );
            }
            try {
                value = Long.parseLong(rawText);
            } catch (NumberFormatException exception) {
                throw new IllegalStateException(
                    "Invalid numeric value at " + sourceDescription + " -> " + key,
                    exception
                );
            }
        }

        if (value < 0L) {
            throw new IllegalStateException(
                "Value must be non-negative at " + sourceDescription + " -> " + key
            );
        }
        return value;
    }

    private String stringValue(Object rawValue) {
        return rawValue == null ? null : rawValue.toString();
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

    private record LoadedSequenceDefinition(
        SequenceDefinition definition,
        String sourcePath
    ) {
    }

    private record EffectSyncOptions(
        String syncDurationWith,
        SyncDurationMode syncDurationMode,
        double syncDurationScale,
        long syncDurationMinMs,
        long syncDurationMaxMs
    ) {
    }

    private static final class LoadStats {
        private final List<String> failures = new ArrayList<>();
        private int directoryFiles;
        private int skippedCount;
        private int duplicateCount;
    }

    private static final class SkippedSequenceDefinitionException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
