package com.eltena.core.application.yaml;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public final class DefinitionFileLayout {

    private DefinitionFileLayout() {
    }

    public static Layout load(JavaPlugin plugin, String aggregateResourcePath, String rootKey, String defaultFolderPath) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(aggregateResourcePath, "aggregateResourcePath");
        Objects.requireNonNull(rootKey, "rootKey");
        Objects.requireNonNull(defaultFolderPath, "defaultFolderPath");

        ensureResource(plugin, aggregateResourcePath);
        File aggregateFile = resolveDataFile(plugin, aggregateResourcePath);
        YamlConfiguration aggregateYaml = new YamlConfiguration();
        try {
            aggregateYaml.load(aggregateFile);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning(
                "[EltenaCore] Failed to load aggregate file '" + aggregateResourcePath + "': " + exception.getMessage()
            );
        }

        boolean enabled = aggregateYaml.getBoolean("enabled", true);
        boolean legacyEnabled = aggregateYaml.getBoolean("legacy-enabled", true);
        String configuredFolder = aggregateYaml.getString("load-folder", defaultFolderPath);
        String loadFolder = normalizeFolderPath(configuredFolder == null || configuredFolder.isBlank() ? defaultFolderPath : configuredFolder);
        File folder = resolveDataFile(plugin, loadFolder);
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning(
                "[EltenaCore] Failed to create definition folder '" + loadFolder + "'."
            );
        }

        return new Layout(
            aggregateResourcePath,
            rootKey,
            aggregateFile,
            aggregateYaml,
            enabled,
            legacyEnabled,
            loadFolder,
            folder,
            aggregateYaml.getConfigurationSection("defaults"),
            aggregateYaml.getConfigurationSection(rootKey)
        );
    }

    public static List<DefinitionDocument> loadFolderDocuments(JavaPlugin plugin, Layout layout) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(layout, "layout");

        if (!layout.enabled()) {
            return List.of();
        }

        if (!layout.folder().isDirectory()) {
            return List.of();
        }

        Path rootPath = layout.folder().toPath();
        List<Path> files;
        try (Stream<Path> stream = Files.walk(rootPath)) {
            files = stream
                .filter(Files::isRegularFile)
                .filter(path -> isYamlFile(path.getFileName().toString()))
                .filter(path -> !isIgnoredRelativePath(rootPath.relativize(path)))
                .sorted(Comparator.comparing(path -> normalizePath(rootPath.relativize(path))))
                .toList();
        } catch (IOException exception) {
            plugin.getLogger().warning(
                "[EltenaCore] Failed to scan definition folder '"
                    + relativePath(layout.folder(), plugin.getDataFolder())
                    + "': " + exception.getMessage()
            );
            return List.of();
        }

        List<DefinitionDocument> documents = new ArrayList<>();
        for (Path path : files) {
            File file = path.toFile();
            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(file);
            } catch (IOException | InvalidConfigurationException exception) {
                plugin.getLogger().warning(
                    "[EltenaCore] Failed to load definition file '" + relativePath(file, plugin.getDataFolder()) + "': "
                        + exception.getMessage()
                );
                continue;
            }
            documents.add(new DefinitionDocument(file, yaml, stripExtension(file.getName())));
        }
        return List.copyOf(documents);
    }

    public static YamlConfiguration mergeDefaults(ConfigurationSection defaults, ConfigurationSection primary) {
        YamlConfiguration merged = new YamlConfiguration();
        if (defaults != null) {
            copySection(defaults, merged, "");
        }
        if (primary != null) {
            copySection(primary, merged, "");
        }
        return merged;
    }

    public static String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index <= 0 ? fileName : fileName.substring(0, index);
    }

    public static String relativePath(File file, File root) {
        String rootPath = root.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        if (filePath.startsWith(rootPath)) {
            String relative = filePath.substring(rootPath.length());
            if (relative.startsWith(File.separator)) {
                relative = relative.substring(1);
            }
            return relative.replace(File.separatorChar, '/');
        }
        return filePath.replace(File.separatorChar, '/');
    }

    public static File resolveDataFile(JavaPlugin plugin, String relativePath) {
        return new File(plugin.getDataFolder(), normalizeFolderPath(relativePath).replace('/', File.separatorChar));
    }

    private static void ensureResource(JavaPlugin plugin, String resourcePath) {
        File file = resolveDataFile(plugin, resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
        }
    }

    private static boolean isYamlFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".yml") || lower.endsWith(".yaml");
    }

    private static boolean isIgnoredRelativePath(Path relativePath) {
        Set<String> ignoredDirectoryNames = Set.of("disabled", "_disabled", "backup", "_backup", "docs", "examples");
        for (Path segment : relativePath) {
            String normalized = segment.toString().trim().toLowerCase(Locale.ROOT);
            if (ignoredDirectoryNames.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeFolderPath(String raw) {
        return raw.replace('\\', '/');
    }

    private static String normalizePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static void copySection(ConfigurationSection source, YamlConfiguration target, String prefix) {
        for (String key : source.getKeys(false)) {
            String path = prefix.isBlank() ? key : prefix + "." + key;
            ConfigurationSection nested = source.getConfigurationSection(key);
            if (nested != null) {
                copySection(nested, target, path);
                continue;
            }
            target.set(path, source.get(key));
        }
    }

    public record Layout(
        String aggregateResourcePath,
        String rootKey,
        File aggregateFile,
        YamlConfiguration aggregateYaml,
        boolean enabled,
        boolean legacyEnabled,
        String loadFolder,
        File folder,
        ConfigurationSection defaults,
        ConfigurationSection legacyRoot
    ) {
    }

    public record DefinitionDocument(
        File file,
        YamlConfiguration yaml,
        String fileStem
    ) {
    }
}
