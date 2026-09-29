package com.eltena.effectcore.config;

import com.eltena.effectcore.EltenaEffectCorePlugin;
import com.eltena.effectcore.virtualcamera.VirtualCameraConfiguration;
import com.eltena.effectcore.virtualcamera.VirtualCameraPreset;
import java.util.ArrayList;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

public final class VirtualCameraConfigRegistry {
    private static final String MODE_RELATIVE_PLAYER = "relative_player";
    private static final String MODE_FIXED_WORLD = "fixed_world";
    private static final String MODE_KEYFRAMED = "keyframed";
    private static final Set<String> ALLOWED_KEYFRAME_EASINGS = Set.of(
        "linear",
        "ease-in",
        "ease-out",
        "ease-in-out",
        "catmull-rom"
    );

    private final EltenaEffectCorePlugin plugin;
    private VirtualCameraConfiguration configuration = VirtualCameraConfiguration.defaults();
    private LoadSummary lastLoadSummary = LoadSummary.empty();

    public VirtualCameraConfigRegistry(EltenaEffectCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        VirtualCameraConfiguration defaults = VirtualCameraConfiguration.defaults();
        File rootFile = new File(plugin.getDataFolder(), "camera/camera.yml");
        YamlConfiguration rootYaml = YamlConfiguration.loadConfiguration(rootFile);
        ConfigurationSection cameraSection = rootYaml.getConfigurationSection("camera");
        if (cameraSection == null) {
            File presetDirectory = new File(plugin.getDataFolder(), "camera/" + defaults.loading().presetDirectory());
            plugin.getLogger().warning("camera/camera.yml に camera セクションがありません。VirtualCamera の既定値を使用します。");
            this.configuration = defaults;
            this.lastLoadSummary = new LoadSummary(
                rootFile.getAbsolutePath(),
                false,
                presetDirectory.getAbsolutePath(),
                0,
                0,
                0,
                0,
                0,
                defaults.defaultPreset(),
                defaults.resolvedDefaultPreset()
            );
            return;
        }

        String configuredDefaultPreset = readString(cameraSection, "default-preset", defaults.defaultPreset());
        VirtualCameraConfiguration.Loading loading = new VirtualCameraConfiguration.Loading(
            readString(cameraSection, "loading.preset-directory", defaults.loading().presetDirectory()),
            cameraSection.getBoolean("loading.fail-on-invalid-preset", defaults.loading().failOnInvalidPreset())
        );
        VirtualCameraConfiguration.Behavior behavior = new VirtualCameraConfiguration.Behavior(
            cameraSection.getBoolean(
                "behavior.restore-player-camera-on-disable",
                defaults.behavior().restorePlayerCameraOnDisable()
            ),
            cameraSection.getBoolean(
                "behavior.reset-state-on-world-change",
                defaults.behavior().resetStateOnWorldChange()
            ),
            cameraSection.getBoolean(
                "behavior.reset-state-on-server-switch",
                defaults.behavior().resetStateOnServerSwitch()
            )
        );

        File presetDirectory = new File(plugin.getDataFolder(), "camera/" + loading.presetDirectory());
        LoadPresetsResult presetResult = loadPresets(presetDirectory, loading.failOnInvalidPreset());
        LinkedHashMap<String, VirtualCameraPreset> presets = presetResult.presets();

        this.configuration = new VirtualCameraConfiguration(
            cameraSection.getBoolean("enabled", defaults.enabled()),
            configuredDefaultPreset,
            cameraSection.getBoolean("sync-on-join", defaults.syncOnJoin()),
            cameraSection.getBoolean("sync-on-reload", defaults.syncOnReload()),
            loading,
            behavior,
            presets
        );

        int disabledPresets = 0;
        for (VirtualCameraPreset preset : presets.values()) {
            if (!preset.enabled()) {
                disabledPresets++;
            }
        }

        this.lastLoadSummary = new LoadSummary(
            rootFile.getAbsolutePath(),
            true,
            presetDirectory.getAbsolutePath(),
            presetResult.directoryFiles(),
            presets.size(),
            presetResult.skippedInvalid(),
            presetResult.duplicateIds(),
            disabledPresets,
            configuredDefaultPreset,
            configuration.resolvedDefaultPreset()
        );
    }

    public VirtualCameraConfiguration configuration() {
        return configuration;
    }

    public LoadSummary lastLoadSummary() {
        return lastLoadSummary;
    }

    private LoadPresetsResult loadPresets(File directory, boolean failOnInvalidPreset) {
        LinkedHashMap<String, VirtualCameraPreset> presets = new LinkedHashMap<>();
        LinkedHashMap<String, String> presetSources = new LinkedHashMap<>();
        if (!directory.isDirectory()) {
            plugin.getLogger().warning("VirtualCamera preset ディレクトリが見つかりません: " + directory.getAbsolutePath());
            return new LoadPresetsResult(presets, 0, 0, 0);
        }

        File[] files = directory.listFiles(file -> file.isFile() && file.getName().toLowerCase().endsWith(".yml"));
        if (files == null) {
            plugin.getLogger().warning("VirtualCamera preset ディレクトリの一覧取得に失敗しました: " + directory.getAbsolutePath());
            return new LoadPresetsResult(presets, 0, 0, 0);
        }

        Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        int skipped = 0;
        int duplicates = 0;
        for (File file : files) {
            try {
                VirtualCameraPreset preset = loadPreset(file);
                if (presets.containsKey(preset.id())) {
                    duplicates++;
                    String message = "VirtualCamera preset id が重複したため、後から読んだ定義を無効化します。id="
                        + preset.id()
                        + " kept=" + presetSources.get(preset.id())
                        + " ignored=" + file.getAbsolutePath();
                    if (failOnInvalidPreset) {
                        throw new IllegalStateException(message);
                    }
                    plugin.getLogger().warning(message);
                    continue;
                }
                presets.put(preset.id(), preset);
                presetSources.put(preset.id(), file.getAbsolutePath());
            } catch (RuntimeException exception) {
                skipped++;
                String message = "VirtualCamera preset を読み込めなかったため、その preset だけ無効化します。file="
                    + file.getAbsolutePath()
                    + " message=" + exception.getMessage();
                if (failOnInvalidPreset) {
                    throw new IllegalStateException(message, exception);
                }
                plugin.getLogger().warning(message);
            }
        }

        return new LoadPresetsResult(presets, files.length, skipped, duplicates);
    }

    private VirtualCameraPreset loadPreset(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("preset");
        if (section == null) {
            throw new IllegalArgumentException("preset セクションがありません");
        }

        String id = requireString(section, "id", "preset id");
        String displayName = requireString(section, "display-name", "display-name");
        String mode = normalizeMode(readString(section, "mode", MODE_RELATIVE_PLAYER));

        VirtualCameraPreset.RelativePosition relativePosition = null;
        VirtualCameraPreset.WorldPosition worldPosition = null;
        VirtualCameraPreset.Rotation rotation = new VirtualCameraPreset.Rotation(0.0F, 0.0F, 0.0F);
        List<VirtualCameraPreset.Keyframe> keyframes = List.of();

        if (MODE_FIXED_WORLD.equals(mode)) {
            worldPosition = parseWorldPosition(requireSection(section, "world-position"), "world-position");
            rotation = parseRotation(requireSection(section, "rotation"));
        } else if (MODE_KEYFRAMED.equals(mode)) {
            ConfigurationSection rotationSection = section.getConfigurationSection("rotation");
            if (rotationSection != null) {
                rotation = parseRotation(rotationSection);
            }
            keyframes = parseKeyframes(section);
        } else {
            ConfigurationSection relativeSection = section.getConfigurationSection("relative-position");
            if (relativeSection == null) {
                relativeSection = requireSection(section, "position");
            }
            relativePosition = new VirtualCameraPreset.RelativePosition(
                relativeSection.getDouble("distance", 6.0D),
                relativeSection.getDouble("vertical-offset", 2.2D),
                relativeSection.getDouble("horizontal-offset", 1.2D),
                relativeSection.getDouble("forward-offset", 0.0D)
            );
            ConfigurationSection rotationSection = section.getConfigurationSection("rotation");
            rotation = new VirtualCameraPreset.Rotation(
                rotationSection == null ? 0.0F : (float) rotationSection.getDouble("yaw", 0.0D),
                rotationSection == null ? 0.0F : (float) rotationSection.getDouble("pitch", 0.0D),
                rotationSection == null ? 0.0F : (float) rotationSection.getDouble("roll", 0.0D)
            );
        }

        ConfigurationSection movementSection = requireSection(section, "movement");
        ConfigurationSection smoothingSection = requireSection(section, "smoothing");
        ConfigurationSection behaviorSection = requireSection(section, "behavior");

        return new VirtualCameraPreset(
            id,
            displayName,
            section.getBoolean("enabled", true),
            mode,
            relativePosition,
            worldPosition,
            rotation,
            new VirtualCameraPreset.Movement(
                movementSection.getBoolean("allow-player-movement", true),
                movementSection.getBoolean("allow-camera-movement", false),
                movementSection.getBoolean("lock-player-input", false)
            ),
            new VirtualCameraPreset.Smoothing(
                smoothingSection.getBoolean("enabled", true),
                smoothingSection.getDouble("position-smooth-time", 0.18D),
                smoothingSection.getDouble("rotation-smooth-time", 0.12D)
            ),
            new VirtualCameraPreset.Behavior(
                behaviorSection.getBoolean("reset-on-disable", true),
                behaviorSection.getBoolean("restore-player-camera-on-disable", true),
                behaviorSection.getBoolean("use-virtual-entity-camera", true)
            ),
            keyframes
        );
    }

    private List<VirtualCameraPreset.Keyframe> parseKeyframes(ConfigurationSection section) {
        List<Map<?, ?>> rawKeyframes = section.getMapList("keyframes");
        if (rawKeyframes.isEmpty()) {
            throw new IllegalArgumentException("keyframes must contain at least one entry for keyframed presets");
        }

        List<VirtualCameraPreset.Keyframe> keyframes = new ArrayList<>();
        long previousTimeMs = Long.MIN_VALUE;
        for (int index = 0; index < rawKeyframes.size(); index++) {
            Map<?, ?> rawKeyframe = rawKeyframes.get(index);
            String prefix = "keyframes[" + index + "]";

            long timeMs = requireLong(rawKeyframe, "time-ms", prefix + ".time-ms");
            if (timeMs <= previousTimeMs) {
                throw new IllegalArgumentException(prefix + ".time-ms must be strictly increasing");
            }
            previousTimeMs = timeMs;

            VirtualCameraPreset.WorldPosition worldPosition = null;
            if (rawKeyframe.containsKey("world-position")) {
                worldPosition = parseWorldPosition(rawKeyframe.get("world-position"), prefix + ".world-position");
            }

            VirtualCameraPreset.LookAt lookAt = null;
            if (rawKeyframe.containsKey("look-at")) {
                lookAt = parseLookAt(rawKeyframe.get("look-at"), prefix + ".look-at");
            }

            Double fov = rawKeyframe.containsKey("fov") ? requireDouble(rawKeyframe, "fov", prefix + ".fov") : null;
            String easing = normalizeKeyframeEasing(readString(rawKeyframe, "easing", "linear"), prefix + ".easing");

            keyframes.add(new VirtualCameraPreset.Keyframe(timeMs, worldPosition, lookAt, fov, easing));
        }

        return List.copyOf(keyframes);
    }

    private VirtualCameraPreset.LookAt parseLookAt(Object rawLookAt, String path) {
        Map<?, ?> lookAt = requireMap(rawLookAt, path);
        boolean hasWorldPosition = lookAt.containsKey("world-position");
        boolean hasEntityRef = readString(lookAt, "entity-ref", null) != null;
        boolean hasTargetPosition = lookAt.containsKey("target-position");

        if (hasWorldPosition && (hasEntityRef || hasTargetPosition)) {
            throw new IllegalArgumentException(path + " cannot define both world-position and entity-ref/target-position");
        }
        if (hasWorldPosition) {
            return new VirtualCameraPreset.WorldLookAt(
                parseWorldPosition(lookAt.get("world-position"), path + ".world-position")
            );
        }
        if (hasEntityRef) {
            String entityRef = requireString(lookAt, "entity-ref", path + ".entity-ref");
            Vector offset = hasTargetPosition
                ? parseVector(lookAt.get("target-position"), path + ".target-position")
                : new Vector(0.0D, 0.0D, 0.0D);
            return new VirtualCameraPreset.EntityLookAt(entityRef, offset);
        }
        if (hasTargetPosition) {
            throw new IllegalArgumentException(path + ".target-position requires entity-ref");
        }
        throw new IllegalArgumentException(path + " must define either world-position or entity-ref");
    }

    private VirtualCameraPreset.WorldPosition parseWorldPosition(ConfigurationSection section, String path) {
        return new VirtualCameraPreset.WorldPosition(
            requireString(section, "world", path + ".world"),
            section.getDouble("x"),
            section.getDouble("y"),
            section.getDouble("z")
        );
    }

    private VirtualCameraPreset.WorldPosition parseWorldPosition(Object rawWorldPosition, String path) {
        Map<?, ?> worldPosition = requireMap(rawWorldPosition, path);
        return new VirtualCameraPreset.WorldPosition(
            requireString(worldPosition, "world", path + ".world"),
            requireDouble(worldPosition, "x", path + ".x"),
            requireDouble(worldPosition, "y", path + ".y"),
            requireDouble(worldPosition, "z", path + ".z")
        );
    }

    private VirtualCameraPreset.Rotation parseRotation(ConfigurationSection rotationSection) {
        return new VirtualCameraPreset.Rotation(
            (float) rotationSection.getDouble("yaw", 0.0D),
            (float) rotationSection.getDouble("pitch", 0.0D),
            (float) rotationSection.getDouble("roll", 0.0D)
        );
    }

    private Vector parseVector(Object rawVector, String path) {
        Map<?, ?> vector = requireMap(rawVector, path);
        return new Vector(
            requireDouble(vector, "x", path + ".x"),
            requireDouble(vector, "y", path + ".y"),
            requireDouble(vector, "z", path + ".z")
        );
    }

    private String normalizeKeyframeEasing(String rawEasing, String path) {
        String normalized = rawEasing.toLowerCase(Locale.ROOT);
        if (!ALLOWED_KEYFRAME_EASINGS.contains(normalized)) {
            throw new IllegalArgumentException(
                path + " must be one of " + String.join(", ", ALLOWED_KEYFRAME_EASINGS)
            );
        }
        return normalized;
    }

    private String normalizeMode(String rawMode) {
        if (MODE_FIXED_WORLD.equalsIgnoreCase(rawMode)) {
            return MODE_FIXED_WORLD;
        }
        if (MODE_KEYFRAMED.equalsIgnoreCase(rawMode)) {
            return MODE_KEYFRAMED;
        }
        if (MODE_RELATIVE_PLAYER.equalsIgnoreCase(rawMode)
            || "virtual_entity".equalsIgnoreCase(rawMode)
            || "relative".equalsIgnoreCase(rawMode)) {
            return MODE_RELATIVE_PLAYER;
        }
        throw new IllegalArgumentException("未対応の mode です: " + rawMode);
    }

    private String requireString(ConfigurationSection section, String path, String label) {
        String value = readString(section, path, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " が空です");
        }
        return value;
    }

    private ConfigurationSection requireSection(ConfigurationSection section, String path) {
        ConfigurationSection child = section.getConfigurationSection(path);
        if (child == null) {
            throw new IllegalArgumentException(path + " セクションがありません");
        }
        return child;
    }

    private String readString(ConfigurationSection section, String path, String fallback) {
        String value = section.getString(path, fallback);
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private Map<?, ?> requireMap(Object rawValue, String path) {
        if (rawValue instanceof Map<?, ?> map) {
            return map;
        }
        if (rawValue instanceof ConfigurationSection child) {
            return child.getValues(false);
        }
        throw new IllegalArgumentException(path + " must be an object");
    }

    private String requireString(Map<?, ?> values, String key, String label) {
        String value = readString(values, key, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }

    private String readString(Map<?, ?> values, String key, String fallback) {
        Object rawValue = values.get(key);
        if (rawValue == null) {
            return fallback;
        }
        String trimmed = String.valueOf(rawValue).trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private double requireDouble(Map<?, ?> values, String key, String label) {
        Object rawValue = values.get(key);
        if (rawValue == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        if (rawValue instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(rawValue).trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a number", exception);
        }
    }

    private long requireLong(Map<?, ?> values, String key, String label) {
        Object rawValue = values.get(key);
        if (rawValue == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        if (rawValue instanceof Number number) {
            double value = number.doubleValue();
            if (value != Math.rint(value)) {
                throw new IllegalArgumentException(label + " must be an integer");
            }
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(rawValue).trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be an integer", exception);
        }
    }

    private record LoadPresetsResult(
        LinkedHashMap<String, VirtualCameraPreset> presets,
        int directoryFiles,
        int skippedInvalid,
        int duplicateIds
    ) {
    }

    public record LoadSummary(
        String rootFilePath,
        boolean rootLoaded,
        String presetDirectoryPath,
        int presetFiles,
        int loadedPresets,
        int invalidPresets,
        int duplicatePresets,
        int disabledPresets,
        String configuredDefaultPreset,
        String resolvedDefaultPreset
    ) {
        public static LoadSummary empty() {
            return new LoadSummary("", false, "", 0, 0, 0, 0, 0, "", null);
        }
    }
}
