package com.eltena.soundcore.config;

import com.eltena.soundcore.EltenaSoundCorePlugin;
import com.eltena.soundcore.audio.OggDurationResolver;
import com.eltena.soundcore.live.LiveAssetDefinition;
import com.eltena.soundcore.live.LiveAssetType;
import com.eltena.soundcore.sound.SoundCategory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;

public final class LiveAssetRegistry {
    private static final Set<String> IGNORED_DIRECTORY_NAMES = Set.of(
        "disabled",
        "_disabled",
        "backup",
        "_backup",
        "docs",
        "examples",
        "files"
    );

    private final EltenaSoundCorePlugin plugin;
    private Map<String, LiveAssetDefinition> liveAssets = Map.of();

    public LiveAssetRegistry(EltenaSoundCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        LoadStats stats = new LoadStats();
        LinkedHashMap<String, LoadedLiveAssetDefinition> loadedById = new LinkedHashMap<>();
        loadDirectoryDefinitions(loadedById, stats);

        if (!stats.failures.isEmpty()) {
            throw new IllegalStateException(
                "Failed to load live asset definitions. failureCount=" + stats.failures.size()
            );
        }

        LinkedHashMap<String, LiveAssetDefinition> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, LoadedLiveAssetDefinition> entry : loadedById.entrySet()) {
            resolved.put(entry.getKey(), entry.getValue().definition());
        }
        this.liveAssets = Collections.unmodifiableMap(resolved);

        plugin.logInfo(
            "log.live-asset-load-summary",
            Map.of(
                "loadedCount", Integer.toString(this.liveAssets.size()),
                "directoryFiles", Integer.toString(stats.directoryFiles),
                "skippedCount", Integer.toString(stats.skippedCount),
                "duplicateCount", Integer.toString(stats.duplicateCount)
            )
        );
    }

    public LiveAssetDefinition require(String assetId) {
        LiveAssetDefinition definition = liveAssets.get(assetId);
        if (definition == null) {
            throw new IllegalArgumentException(assetId);
        }
        return definition;
    }

    public List<LiveAssetDefinition> definitions() {
        return List.copyOf(liveAssets.values());
    }

    public Set<String> liveAssetIds() {
        return liveAssets.keySet();
    }

    public int count() {
        return liveAssets.size();
    }

    private void loadDirectoryDefinitions(
        Map<String, LoadedLiveAssetDefinition> loadedById,
        LoadStats stats
    ) {
        File root = new File(plugin.getDataFolder(), "live-assets");
        if (!root.isDirectory()) {
            return;
        }

        List<Path> files = findDefinitionFiles(root.toPath());
        stats.directoryFiles = files.size();
        for (Path filePath : files) {
            loadDefinitionFile(filePath.toFile(), loadedById, stats, root.toPath());
        }
    }

    private void loadDefinitionFile(
        File file,
        Map<String, LoadedLiveAssetDefinition> loadedById,
        LoadStats stats,
        Path rootPath
    ) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String assetId = readTrimmed(yaml.getString("id"));
        if (assetId == null) {
            plugin.logWarning("log.live-asset-skip-missing-id", Map.of("file", formatPath(file)));
            stats.skippedCount++;
            return;
        }

        String type = readTrimmed(yaml.getString("type"));
        if (type == null) {
            plugin.logWarning(
                "log.live-asset-skip-missing-type",
                Map.of("file", formatPath(file), "assetId", assetId)
            );
            stats.skippedCount++;
            return;
        }

        try {
            LiveAssetDefinition definition = parseDefinition(assetId, type, yaml, formatPath(file), rootPath);
            registerDefinition(loadedById, definition, formatPath(file), stats);
        } catch (SkippedLiveAssetDefinitionException exception) {
            stats.skippedCount++;
        } catch (RuntimeException exception) {
            logLoadFailure(formatPath(file), exception);
            stats.failures.add(formatPath(file));
        }
    }

    private LiveAssetDefinition parseDefinition(
        String assetId,
        String typeValue,
        YamlConfiguration yaml,
        String sourceDescription,
        Path rootPath
    ) {
        LiveAssetType type;
        try {
            type = LiveAssetType.fromConfig(typeValue);
        } catch (IllegalArgumentException exception) {
            plugin.logWarning(
                "log.live-asset-skip-unsupported-type",
                Map.of(
                    "file", sourceDescription,
                    "assetId", assetId,
                    "type", typeValue
                )
            );
            throw new SkippedLiveAssetDefinitionException();
        }

        String categoryValue = requireString(yaml, "category", sourceDescription);
        SoundCategory category;
        try {
            category = SoundCategory.fromConfig(categoryValue);
        } catch (IllegalArgumentException exception) {
            plugin.logWarning(
                "log.live-asset-skip-unsupported-category",
                Map.of(
                    "file", sourceDescription,
                    "assetId", assetId,
                    "category", categoryValue
                )
            );
            throw new SkippedLiveAssetDefinitionException();
        }

        String relativeFile = requireString(yaml, "file", sourceDescription);
        Path resolvedFile = resolveAssetFile(rootPath, relativeFile, sourceDescription);
        long sizeBytes = fileSize(resolvedFile, sourceDescription);
        int version = requirePositiveInt(yaml, "version", sourceDescription);
        String configuredSha256 = readTrimmed(yaml.getString("sha256"));
        String actualSha256 = computeSha256(resolvedFile, sourceDescription);
        if (configuredSha256 != null && !configuredSha256.equalsIgnoreCase(actualSha256)) {
            plugin.logWarning(
                "log.live-asset-sha-mismatch-config",
                Map.of(
                    "file", sourceDescription,
                    "assetId", assetId,
                    "configuredSha256", configuredSha256,
                    "actualSha256", actualSha256
                )
            );
            throw new SkippedLiveAssetDefinitionException();
        }

        LiveAssetDefinition definition = new LiveAssetDefinition(
            assetId,
            type,
            requireString(yaml, "display-name", sourceDescription),
            relativeFile.replace('\\', '/'),
            resolvedFile.getFileName().toString(),
            version,
            actualSha256,
            category,
            requirePositiveDouble(yaml, "volume", sourceDescription),
            requirePositiveDouble(yaml, "pitch", sourceDescription),
            resolveDurationMs(yaml, resolvedFile, sourceDescription),
            sizeBytes,
            resolvedFile
        );

        if (plugin.isDebugLogEnabled()) {
            plugin.logInfo(
                "log.live-asset-loaded",
                Map.of(
                    "assetId", definition.id(),
                    "type", definition.type().payloadName(),
                    "version", Integer.toString(definition.version()),
                    "sizeBytes", Long.toString(definition.sizeBytes())
                )
            );
        }
        return definition;
    }

    private long resolveDurationMs(
        YamlConfiguration yaml,
        Path resolvedFile,
        String sourceDescription
    ) {
        if (yaml.contains("duration-ms")) {
            return readNonNegativeLong(yaml, "duration-ms", 0L, sourceDescription);
        }
        long inferredDurationMs = OggDurationResolver.resolveDurationMs(resolvedFile);
        return Math.max(0L, inferredDurationMs);
    }

    private void registerDefinition(
        Map<String, LoadedLiveAssetDefinition> loadedById,
        LiveAssetDefinition definition,
        String sourcePath,
        LoadStats stats
    ) {
        LoadedLiveAssetDefinition existing = loadedById.get(definition.id());
        if (existing == null) {
            loadedById.put(definition.id(), new LoadedLiveAssetDefinition(definition, sourcePath));
            return;
        }

        stats.duplicateCount++;
        stats.skippedCount++;
        plugin.logWarning(
            "log.live-asset-duplicate-id",
            Map.of(
                "assetId", definition.id(),
                "keptFile", existing.sourcePath(),
                "ignoredFile", sourcePath
            )
        );
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
            throw new IllegalStateException("Failed to scan live-assets directory: " + rootPath, exception);
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

    private Path resolveAssetFile(Path rootPath, String relativePath, String sourceDescription) {
        Path resolved = rootPath.resolve(relativePath).normalize();
        if (!resolved.startsWith(rootPath) || !Files.isRegularFile(resolved)) {
            throw new IllegalStateException(
                "Missing live asset file at " + sourceDescription + " -> " + relativePath
            );
        }
        return resolved;
    }

    private long fileSize(Path path, String sourceDescription) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Failed to read live asset size at " + sourceDescription + " -> " + path,
                exception
            );
        }
    }

    private String computeSha256(Path path, String sourceDescription) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(path);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Failed to read live asset file at " + sourceDescription + " -> " + path,
                exception
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private void logLoadFailure(String sourcePath, RuntimeException exception) {
        if (exception instanceof SkippedLiveAssetDefinitionException) {
            return;
        }
        String message = exception.getMessage() == null
            ? exception.getClass().getSimpleName()
            : exception.getMessage();
        plugin.log(
            Level.SEVERE,
            "log.live-asset-file-error",
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

    private int requirePositiveInt(YamlConfiguration yaml, String path, String sourceDescription) {
        if (!yaml.contains(path)) {
            throw new IllegalStateException("Missing numeric value at " + sourceDescription + " -> " + path);
        }
        int value = yaml.getInt(path);
        if (value <= 0) {
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

    private record LoadedLiveAssetDefinition(
        LiveAssetDefinition definition,
        String sourcePath
    ) {
    }

    private static final class LoadStats {
        private final List<String> failures = new ArrayList<>();
        private int directoryFiles;
        private int skippedCount;
        private int duplicateCount;
    }

    private static final class SkippedLiveAssetDefinitionException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
