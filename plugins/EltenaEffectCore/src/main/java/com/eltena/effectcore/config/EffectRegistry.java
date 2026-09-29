package com.eltena.effectcore.config;

import com.eltena.effectcore.EltenaEffectCorePlugin;
import com.eltena.effectcore.effect.DistanceFalloffDefinition;
import com.eltena.effectcore.effect.DistanceFalloffType;
import com.eltena.effectcore.effect.EarthquakeEffectDefinition;
import com.eltena.effectcore.effect.EarthquakeShakePattern;
import com.eltena.effectcore.effect.EffectDefinition;
import com.eltena.effectcore.effect.EffectScope;
import com.eltena.effectcore.effect.RepeatSettings;
import com.eltena.effectcore.effect.ScreenColorDefinition;
import com.eltena.effectcore.effect.ScreenEffectDefinition;
import com.eltena.effectcore.effect.ScreenShaderCurve;
import com.eltena.effectcore.effect.ScreenShaderDirection;
import com.eltena.effectcore.effect.ScreenShaderDefinition;
import com.eltena.effectcore.effect.ScreenShaderType;
import com.eltena.effectcore.effect.ScreenVisualPattern;
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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class EffectRegistry {
    private static final Set<String> IGNORED_DIRECTORY_NAMES = Set.of(
        "disabled",
        "_disabled",
        "backup",
        "_backup",
        "docs",
        "examples"
    );

    private final EltenaEffectCorePlugin plugin;
    private Map<String, EffectDefinition> effects = Map.of();

    public EffectRegistry(EltenaEffectCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        LoadStats stats = new LoadStats();
        LinkedHashMap<String, LoadedEffectDefinition> loadedById = new LinkedHashMap<>();

        loadDirectoryDefinitions(loadedById, stats);
        loadLegacyDefinitions(loadedById, stats);

        if (!stats.failures.isEmpty()) {
            throw new IllegalStateException(
                "Failed to load effect definitions. failureCount=" + stats.failures.size()
            );
        }

        LinkedHashMap<String, EffectDefinition> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, LoadedEffectDefinition> entry : loadedById.entrySet()) {
            resolved.put(entry.getKey(), entry.getValue().definition());
        }
        this.effects = Collections.unmodifiableMap(resolved);

        plugin.logInfo(
            "log.effect-load-summary",
            Map.of(
                "loadedCount", Integer.toString(this.effects.size()),
                "directoryFiles", Integer.toString(stats.directoryFiles),
                "legacyEntries", Integer.toString(stats.legacyEntries),
                "skippedCount", Integer.toString(stats.skippedCount),
                "duplicateCount", Integer.toString(stats.duplicateCount)
            )
        );
    }

    public EffectDefinition require(String effectId) {
        EffectDefinition definition = effects.get(effectId);
        if (definition == null) {
            throw new IllegalArgumentException(effectId);
        }
        return definition;
    }

    public Set<String> effectIds() {
        return effects.keySet();
    }

    public int count() {
        return effects.size();
    }

    private void loadDirectoryDefinitions(
        Map<String, LoadedEffectDefinition> loadedById,
        LoadStats stats
    ) {
        File effectsDirectory = new File(plugin.getDataFolder(), "effects");
        if (!effectsDirectory.isDirectory()) {
            return;
        }

        List<Path> effectFiles = findEffectFiles(effectsDirectory.toPath());
        stats.directoryFiles = effectFiles.size();
        for (Path filePath : effectFiles) {
            loadDirectoryDefinitionFile(filePath.toFile(), loadedById, stats);
        }
    }

    private void loadLegacyDefinitions(
        Map<String, LoadedEffectDefinition> loadedById,
        LoadStats stats
    ) {
        File legacyFile = new File(plugin.getDataFolder(), "effects.yml");
        if (!legacyFile.isFile()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacyFile);
        ConfigurationSection root = yaml.getConfigurationSection("effects");
        if (root == null) {
            plugin.logWarning(
                "log.effect-legacy-missing-root",
                Map.of("file", formatPath(legacyFile))
            );
            stats.skippedCount++;
            return;
        }

        List<String> effectIds = new ArrayList<>(root.getKeys(false));
        effectIds.sort(String::compareTo);
        for (String effectId : effectIds) {
            stats.legacyEntries++;
            ConfigurationSection section = root.getConfigurationSection(effectId);
            if (section == null) {
                plugin.logWarning(
                    "log.effect-file-error",
                    Map.of(
                        "file", formatPath(legacyFile) + "#" + effectId,
                        "message", "missing-section"
                    )
                );
                stats.failures.add(formatPath(legacyFile) + "#" + effectId);
                continue;
            }
            String type = readTrimmed(section.getString("type"));
            if (type == null) {
                plugin.logWarning(
                    "log.effect-skip-missing-type",
                    Map.of(
                        "file", formatPath(legacyFile) + "#" + effectId,
                        "effectId", effectId
                    )
                );
                stats.skippedCount++;
                continue;
            }

            try {
                EffectDefinition definition = parseDefinition(
                    effectId,
                    type,
                    section,
                    formatPath(legacyFile) + "#" + effectId
                );
                registerDefinition(
                    loadedById,
                    definition,
                    formatPath(legacyFile),
                    DefinitionSource.LEGACY,
                    stats
                );
            } catch (SkippedEffectDefinitionException exception) {
                stats.skippedCount++;
            } catch (RuntimeException exception) {
                logLoadFailure(formatPath(legacyFile) + "#" + effectId, exception);
                stats.failures.add(formatPath(legacyFile) + "#" + effectId);
            }
        }
    }

    private void loadDirectoryDefinitionFile(
        File file,
        Map<String, LoadedEffectDefinition> loadedById,
        LoadStats stats
    ) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String effectId = readTrimmed(yaml.getString("id"));
        if (effectId == null) {
            plugin.logWarning(
                "log.effect-skip-missing-id",
                Map.of("file", formatPath(file))
            );
            stats.skippedCount++;
            return;
        }

        String type = readTrimmed(yaml.getString("type"));
        if (type == null) {
            plugin.logWarning(
                "log.effect-skip-missing-type",
                Map.of(
                    "file", formatPath(file),
                    "effectId", effectId
                )
            );
            stats.skippedCount++;
            return;
        }

        try {
            EffectDefinition definition = parseDefinition(
                effectId,
                type,
                yaml,
                formatPath(file)
            );
            registerDefinition(
                loadedById,
                definition,
                formatPath(file),
                DefinitionSource.DIRECTORY,
                stats
            );
        } catch (SkippedEffectDefinitionException exception) {
            stats.skippedCount++;
        } catch (RuntimeException exception) {
            logLoadFailure(formatPath(file), exception);
            stats.failures.add(formatPath(file));
        }
    }

    private EffectDefinition parseDefinition(
        String effectId,
        String type,
        ConfigurationSection section,
        String sourceDescription
    ) {
        if ("earthquake".equalsIgnoreCase(type)) {
            return parseEarthquakeDefinition(effectId, section, sourceDescription);
        }
        if ("screen_effect".equalsIgnoreCase(type)) {
            return parseScreenEffectDefinition(effectId, section, sourceDescription);
        }

        plugin.logWarning(
            "log.effect-skip-unsupported-type",
            Map.of(
                "file", sourceDescription,
                "effectId", effectId,
                "type", type
            )
        );
        throw new SkippedEffectDefinitionException();
    }

    private EarthquakeEffectDefinition parseEarthquakeDefinition(
        String effectId,
        ConfigurationSection section,
        String sourceDescription
    ) {
        ConfigurationSection falloffSection = section.getConfigurationSection("distance-falloff");
        DistanceFalloffDefinition falloff = new DistanceFalloffDefinition(
            DistanceFalloffType.fromConfig(falloffSection == null ? "LINEAR" : falloffSection.getString("type", "LINEAR")),
            falloffSection == null ? 0.0D : requireNonNegativeDouble(falloffSection, "minimum-strength", sourceDescription)
        );

        ConfigurationSection repeatSection = section.getConfigurationSection("repeat");
        RepeatSettings repeat = repeatSection == null || !repeatSection.getBoolean("enabled", false)
            ? RepeatSettings.disabled()
            : new RepeatSettings(
                true,
                requirePositiveLong(repeatSection, "interval-ms", sourceDescription),
                requirePositiveInt(repeatSection, "count", sourceDescription),
                requirePositiveDouble(repeatSection, "power-multiplier", sourceDescription)
            );

        return new EarthquakeEffectDefinition(
            effectId,
            requireString(section, "display-name", sourceDescription),
            EarthquakeShakePattern.fromConfig(section.getString("shake-pattern"), sourceDescription),
            EffectScope.fromConfig(requireString(section, "scope", sourceDescription)),
            requirePositiveDouble(section, "radius", sourceDescription),
            requirePositiveDouble(section, "base-power", sourceDescription),
            requirePositiveDouble(section, "wave-speed", sourceDescription),
            requirePositiveLong(section, "duration-ms", sourceDescription),
            requireNonNegativeLong(section, "rise-ms", sourceDescription),
            requireNonNegativeLong(section, "fall-ms", sourceDescription),
            requireNonNegativeDouble(section, "near-instant-radius", sourceDescription),
            section.getBoolean("distance-delay", true),
            falloff,
            repeat
        );
    }

    private ScreenEffectDefinition parseScreenEffectDefinition(
        String effectId,
        ConfigurationSection section,
        String sourceDescription
    ) {
        ConfigurationSection colorSection = section.getConfigurationSection("color");
        if (colorSection == null) {
            throw new IllegalStateException(
                "Missing section at " + sourceDescription + " -> color"
            );
        }
        ConfigurationSection shaderSection = section.getConfigurationSection("shader");

        return new ScreenEffectDefinition(
            effectId,
            requireString(section, "display-name", sourceDescription),
            ScreenVisualPattern.fromConfig(section.getString("visual-pattern"), sourceDescription),
            requirePositiveLong(section, "duration-ms", sourceDescription),
            requirePositiveDouble(section, "strength", sourceDescription),
            requireNonNegativeLong(section, "fade-in-ms", sourceDescription),
            requireNonNegativeLong(section, "fade-out-ms", sourceDescription),
            new ScreenColorDefinition(
                requireUnitDouble(colorSection, "r", sourceDescription),
                requireUnitDouble(colorSection, "g", sourceDescription),
                requireUnitDouble(colorSection, "b", sourceDescription),
                requireUnitDouble(colorSection, "a", sourceDescription)
            ),
            parseScreenShaderDefinition(shaderSection, sourceDescription)
        );
    }

    private ScreenShaderDefinition parseScreenShaderDefinition(
        ConfigurationSection shaderSection,
        String sourceDescription
    ) {
        if (shaderSection == null || !shaderSection.getBoolean("enabled", false)) {
            return ScreenShaderDefinition.disabled();
        }

        List<ScreenShaderDefinition.Layer> layers = parseScreenShaderLayers(shaderSection, sourceDescription);
        String legacyTypeValue = readTrimmed(shaderSection.getString("type"));
        if (legacyTypeValue == null) {
            if (!layers.isEmpty()) {
                return new ScreenShaderDefinition(
                    true,
                    ScreenShaderType.NONE,
                    0.0D,
                    0,
                    0.0D,
                    0.0D,
                    0.0D,
                    layers
                );
            }
            plugin.getLogger().warning(
                "[EltenaEffectCore] Shader enabled but no legacy shader.type or shader.layers were defined at "
                    + sourceDescription
                    + " -> shader"
            );
            return ScreenShaderDefinition.disabled();
        }

        return new ScreenShaderDefinition(
            true,
            ScreenShaderType.fromConfig(legacyTypeValue, sourceDescription),
            requirePositiveDouble(shaderSection, "strength", sourceDescription),
            Math.min(24, requirePositiveInt(shaderSection, "samples", sourceDescription)),
            requirePositiveDouble(shaderSection, "radius", sourceDescription),
            requireNonNegativeDouble(shaderSection, "chromatic-offset", sourceDescription),
            requireNonNegativeDouble(shaderSection, "pressure-distortion", sourceDescription),
            layers
        );
    }

    private List<ScreenShaderDefinition.Layer> parseScreenShaderLayers(
        ConfigurationSection shaderSection,
        String sourceDescription
    ) {
        List<Map<?, ?>> rawLayers = shaderSection.getMapList("layers");
        if (rawLayers.isEmpty()) {
            return List.of();
        }

        List<ScreenShaderDefinition.Layer> parsedLayers = new ArrayList<>();
        for (int index = 0; index < rawLayers.size(); index++) {
            ScreenShaderDefinition.Layer layer = parseScreenShaderLayer(
                rawLayers.get(index),
                sourceDescription + " -> shader.layers[" + index + "]"
            );
            if (layer != null && layer.active()) {
                parsedLayers.add(layer);
            }
        }
        return List.copyOf(parsedLayers);
    }

    private ScreenShaderDefinition.Layer parseScreenShaderLayer(
        Map<?, ?> rawLayer,
        String sourceDescription
    ) {
        ScreenShaderType type = ScreenShaderType.fromLayerConfig(readLayerString(rawLayer, "type", null));
        if (type == ScreenShaderType.NONE) {
            plugin.getLogger().warning(
                "[EltenaEffectCore] Unsupported shader layer type. Skipping " + sourceDescription
            );
            return null;
        }

        double strength = readLayerDouble(rawLayer, "strength", 0.0D);
        if (strength <= 0.0D) {
            plugin.getLogger().warning(
                "[EltenaEffectCore] Shader layer strength must be positive. Skipping "
                    + sourceDescription
                    + " -> strength"
            );
            return null;
        }

        double startProgress = clamp(readLayerDouble(rawLayer, "start-progress", 0.0D), 0.0D, 1.0D);
        double endProgress = clamp(readLayerDouble(rawLayer, "end-progress", 1.0D), 0.0D, 1.0D);
        if (endProgress <= startProgress) {
            plugin.getLogger().warning(
                "[EltenaEffectCore] Shader layer progress window is invalid. Skipping "
                    + sourceDescription
                    + " start="
                    + startProgress
                    + " end="
                    + endProgress
            );
            return null;
        }

        int samples = clampLayerInt(rawLayer, "samples", type == ScreenShaderType.RADIAL_BLUR ? 8 : 0, 0, 24);
        double radius = Math.max(
            0.0D,
            readLayerDouble(rawLayer, "radius", type == ScreenShaderType.RADIAL_BLUR ? 0.55D : 0.0D)
        );
        int count = clampLayerInt(rawLayer, "count", type == ScreenShaderType.SONIC_PRESSURE_BURST ? 24 : 0, 0, 128);
        double length = Math.max(
            0.0D,
            readLayerDouble(rawLayer, "length", type == ScreenShaderType.SONIC_PRESSURE_BURST ? 0.85D : 0.0D)
        );
        double sharpness = Math.max(
            0.0D,
            readLayerDouble(rawLayer, "sharpness", type == ScreenShaderType.SONIC_PRESSURE_BURST ? 12.0D : 0.0D)
        );
        double centerBias = Math.max(
            0.0D,
            readLayerDouble(rawLayer, "center-bias", type == ScreenShaderType.SONIC_PRESSURE_BURST ? 0.35D : 0.0D)
        );
        double edgeBias = Math.max(
            0.0D,
            readLayerDouble(rawLayer, "edge-bias", type == ScreenShaderType.SONIC_PRESSURE_BURST ? 1.0D : 0.0D)
        );
        double chromaticOffset = Math.max(
            0.0D,
            readLayerDouble(rawLayer, "chromatic-offset", type == ScreenShaderType.SONIC_PRESSURE_BURST ? 0.008D : 0.0D)
        );
        double frequency = Math.max(0.0D, readLayerDouble(rawLayer, "frequency", 8.0D));
        double speed = Math.max(0.0D, readLayerDouble(rawLayer, "speed", 1.0D));
        boolean edgeOnly = readLayerBoolean(rawLayer, "edge-only", false);
        ScreenShaderDirection direction = ScreenShaderDirection.fromConfig(readLayerString(rawLayer, "direction", null));
        ScreenShaderCurve curve = ScreenShaderCurve.fromConfig(readLayerString(rawLayer, "curve", null));
        ScreenShaderDefinition.Color color = parseLayerColor(rawLayer.get("color"));
        double noiseStrength = Math.max(0.0D, readLayerDouble(rawLayer, "noise-strength", 0.0D));
        double wobble = Math.max(0.0D, readLayerDouble(rawLayer, "wobble", 0.0D));
        double blurScale = Math.max(0.0D, readLayerDouble(rawLayer, "blur-scale", 0.0D));
        double refractionStrength = Math.max(0.0D, readLayerDouble(rawLayer, "refraction-strength", 0.0D));
        double desaturation = Math.max(0.0D, readLayerDouble(rawLayer, "desaturation", 0.0D));
        double smear = Math.max(0.0D, readLayerDouble(rawLayer, "smear", 0.0D));
        double pulse = Math.max(0.0D, readLayerDouble(rawLayer, "pulse", 0.0D));

        return new ScreenShaderDefinition.Layer(
            type,
            strength,
            samples,
            radius,
            frequency,
            speed,
            edgeOnly,
            direction,
            startProgress,
            endProgress,
            curve,
            color,
            noiseStrength,
            wobble,
            blurScale,
            refractionStrength,
            desaturation,
            smear,
            pulse,
            count,
            length,
            sharpness,
            centerBias,
            edgeBias,
            chromaticOffset
        );
    }

    private void registerDefinition(
        Map<String, LoadedEffectDefinition> loadedById,
        EffectDefinition definition,
        String sourcePath,
        DefinitionSource source,
        LoadStats stats
    ) {
        LoadedEffectDefinition existing = loadedById.get(definition.id());
        if (existing == null) {
            loadedById.put(definition.id(), new LoadedEffectDefinition(definition, sourcePath, source));
            return;
        }

        stats.duplicateCount++;
        boolean newDefinitionWins = source == DefinitionSource.DIRECTORY
            && existing.source() == DefinitionSource.LEGACY;
        plugin.logWarning(
            "log.effect-duplicate-id",
            Map.of(
                "effectId", definition.id(),
                "keptFile", newDefinitionWins ? sourcePath : existing.sourcePath(),
                "ignoredFile", newDefinitionWins ? existing.sourcePath() : sourcePath
            )
        );

        if (newDefinitionWins) {
            loadedById.put(definition.id(), new LoadedEffectDefinition(definition, sourcePath, source));
        } else {
            stats.skippedCount++;
        }
    }

    private List<Path> findEffectFiles(Path rootPath) {
        try (Stream<Path> stream = Files.walk(rootPath)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(this::isYamlFile)
                .filter(path -> !isIgnoredRelativePath(rootPath.relativize(path)))
                .sorted(Comparator.comparing(path -> normalizePath(rootPath.relativize(path))))
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan effect directory: " + rootPath, exception);
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
        if (exception instanceof SkippedEffectDefinitionException) {
            return;
        }
        String message = exception.getMessage() == null
            ? exception.getClass().getSimpleName()
            : exception.getMessage();
        plugin.log(
            Level.SEVERE,
            "log.effect-file-error",
            Map.of(
                "file", sourcePath,
                "message", message
            ),
            exception
        );
    }

    private String requireString(ConfigurationSection section, String path, String sourceDescription) {
        String value = section.getString(path);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing string value at " + sourceDescription + " -> " + path);
        }
        return value;
    }

    private double requirePositiveDouble(ConfigurationSection section, String path, String sourceDescription) {
        if (!section.contains(path)) {
            throw new IllegalStateException("Missing numeric value at " + sourceDescription + " -> " + path);
        }
        double value = section.getDouble(path);
        if (value <= 0.0D) {
            throw new IllegalStateException("Value must be positive at " + sourceDescription + " -> " + path);
        }
        return value;
    }

    private double requireNonNegativeDouble(ConfigurationSection section, String path, String sourceDescription) {
        if (!section.contains(path)) {
            throw new IllegalStateException("Missing numeric value at " + sourceDescription + " -> " + path);
        }
        double value = section.getDouble(path);
        if (value < 0.0D) {
            throw new IllegalStateException("Value must be non-negative at " + sourceDescription + " -> " + path);
        }
        return value;
    }

    private double requireUnitDouble(ConfigurationSection section, String path, String sourceDescription) {
        double value = requireNonNegativeDouble(section, path, sourceDescription);
        if (value > 1.0D) {
            throw new IllegalStateException(
                "Value must be between 0.0 and 1.0 at " + sourceDescription + " -> " + path
            );
        }
        return value;
    }

    private long requirePositiveLong(ConfigurationSection section, String path, String sourceDescription) {
        if (!section.contains(path)) {
            throw new IllegalStateException("Missing numeric value at " + sourceDescription + " -> " + path);
        }
        long value = section.getLong(path);
        if (value <= 0L) {
            throw new IllegalStateException("Value must be positive at " + sourceDescription + " -> " + path);
        }
        return value;
    }

    private long requireNonNegativeLong(ConfigurationSection section, String path, String sourceDescription) {
        if (!section.contains(path)) {
            throw new IllegalStateException("Missing numeric value at " + sourceDescription + " -> " + path);
        }
        long value = section.getLong(path);
        if (value < 0L) {
            throw new IllegalStateException("Value must be non-negative at " + sourceDescription + " -> " + path);
        }
        return value;
    }

    private int requirePositiveInt(ConfigurationSection section, String path, String sourceDescription) {
        if (!section.contains(path)) {
            throw new IllegalStateException("Missing numeric value at " + sourceDescription + " -> " + path);
        }
        int value = section.getInt(path);
        if (value <= 0) {
            throw new IllegalStateException("Value must be positive at " + sourceDescription + " -> " + path);
        }
        return value;
    }

    private String readLayerString(Map<?, ?> rawLayer, String key, String fallback) {
        Object rawValue = rawLayer.get(key);
        if (rawValue == null) {
            return fallback;
        }
        String value = rawValue.toString().trim();
        return value.isEmpty() ? fallback : value;
    }

    private double readLayerDouble(Map<?, ?> rawLayer, String key, double fallback) {
        Object rawValue = rawLayer.get(key);
        if (rawValue == null) {
            return fallback;
        }
        if (rawValue instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(rawValue.toString().trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int clampLayerInt(Map<?, ?> rawLayer, String key, int fallback, int min, int max) {
        Object rawValue = rawLayer.get(key);
        int value = fallback;
        if (rawValue instanceof Number number) {
            value = number.intValue();
        } else if (rawValue != null) {
            try {
                value = Integer.parseInt(rawValue.toString().trim());
            } catch (NumberFormatException ignored) {
                value = fallback;
            }
        }
        return Math.max(min, Math.min(max, value));
    }

    private boolean readLayerBoolean(Map<?, ?> rawLayer, String key, boolean fallback) {
        Object rawValue = rawLayer.get(key);
        if (rawValue == null) {
            return fallback;
        }
        if (rawValue instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(rawValue.toString().trim());
    }

    @SuppressWarnings("unchecked")
    private ScreenShaderDefinition.Color parseLayerColor(Object rawValue) {
        if (!(rawValue instanceof Map<?, ?> rawColor)) {
            return ScreenShaderDefinition.Color.transparent();
        }

        return new ScreenShaderDefinition.Color(
            clamp(readLayerDouble((Map<?, ?>) rawColor, "r", 1.0D), 0.0D, 1.0D),
            clamp(readLayerDouble((Map<?, ?>) rawColor, "g", 1.0D), 0.0D, 1.0D),
            clamp(readLayerDouble((Map<?, ?>) rawColor, "b", 1.0D), 0.0D, 1.0D),
            clamp(readLayerDouble((Map<?, ?>) rawColor, "a", 0.0D), 0.0D, 1.0D)
        );
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

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum DefinitionSource {
        DIRECTORY,
        LEGACY
    }

    private record LoadedEffectDefinition(
        EffectDefinition definition,
        String sourcePath,
        DefinitionSource source
    ) {
    }

    private static final class LoadStats {
        private final List<String> failures = new ArrayList<>();
        private int directoryFiles;
        private int legacyEntries;
        private int skippedCount;
        private int duplicateCount;
    }

    private static final class SkippedEffectDefinitionException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
