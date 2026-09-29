package com.eltena.core.i18n;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.stream.Stream;

public final class MessageService {

    private static final List<String> LANGUAGE_FILES = List.of(
        "common.yml",
        "command.yml",
        "reset.yml",
        "ability.yml",
        "skill.yml",
        "growth.yml",
        "title.yml",
        "menu.yml",
        "combat.yml",
        "system.yml"
    );

    private final JavaPlugin plugin;
    private YamlConfiguration messages;
    private YamlConfiguration languageMessages;

    public MessageService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void reload() {
        Path dataFolder = plugin.getDataFolder().toPath();
        Path messagesFile = dataFolder.resolve("messages.yml");
        ensureBundledResource(messagesFile, "messages.yml");
        this.messages = loadConfiguration(messagesFile, "messages.yml");

        Path languageFolder = dataFolder.resolve("language");
        ensureLanguageFolder(languageFolder);
        this.languageMessages = loadLanguageConfigurations(languageFolder);
    }

    public String get(String key) {
        ensureLoaded();
        String languageValue = languageMessages.getString(key);
        if (languageValue != null) {
            return languageValue;
        }
        return messages.getString(key, key);
    }

    public String get(String key, Object... pairs) {
        ensureLoaded();
        String value = get(key);
        Map<String, String> replacements = new LinkedHashMap<>();
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            replacements.put(String.valueOf(pairs[index]), String.valueOf(pairs[index + 1]));
        }
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return value;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        ensureLoaded();
        Object value = findValue(key);
        return value instanceof Boolean booleanValue ? booleanValue : defaultValue;
    }

    public double getDouble(String key, double defaultValue) {
        ensureLoaded();
        Object value = findValue(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        ensureLoaded();
        Object value = findValue(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private void ensureLoaded() {
        if (messages == null) {
            reload();
        }
    }

    private Object findValue(String key) {
        Object languageValue = languageMessages.get(key);
        return languageValue != null ? languageValue : messages.get(key);
    }

    private void ensureBundledResource(Path file, String resourcePath) {
        if (Files.exists(file)) {
            return;
        }
        if (plugin.getResource(resourcePath) != null) {
            plugin.saveResource(resourcePath, false);
        }
    }

    private void ensureLanguageFolder(Path languageFolder) {
        try {
            Files.createDirectories(languageFolder);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create language directory", exception);
            return;
        }
        for (String fileName : LANGUAGE_FILES) {
            syncBundledLanguageFile(languageFolder.resolve(fileName), "language/" + fileName);
        }
    }

    private YamlConfiguration loadConfiguration(Path file, String resourcePath) {
        YamlConfiguration defaults = loadBundledConfiguration(resourcePath);
        YamlConfiguration configuration = new YamlConfiguration();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                configuration.load(reader);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load " + file.getFileName(), exception);
                configuration = new YamlConfiguration();
            }
        }
        configuration.setDefaults(defaults);
        return configuration;
    }

    private YamlConfiguration loadBundledConfiguration(String resourcePath) {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream == null) {
                return defaults;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                defaults.load(reader);
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load bundled " + resourcePath, exception);
        }
        return defaults;
    }

    private YamlConfiguration loadLanguageConfigurations(Path languageFolder) {
        YamlConfiguration merged = new YamlConfiguration();
        if (Files.notExists(languageFolder)) {
            return merged;
        }
        try (Stream<Path> stream = Files.list(languageFolder)) {
            stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".yml"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .forEach(path -> mergeLanguageConfiguration(merged, languageFolder, path));
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load language directory", exception);
        }
        return merged;
    }

    private void mergeLanguageConfiguration(YamlConfiguration target, Path languageFolder, Path file) {
        String resourcePath = "language/" + languageFolder.relativize(file).toString().replace('\\', '/');
        YamlConfiguration defaults = loadBundledConfiguration(resourcePath);
        YamlConfiguration configuration = new YamlConfiguration();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            configuration.load(reader);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load language file " + file.getFileName(), exception);
            return;
        }
        mergeInto(target, defaults);
        mergeInto(target, configuration);
    }

    private void mergeInto(YamlConfiguration target, YamlConfiguration source) {
        for (String key : source.getKeys(true)) {
            if (source.isConfigurationSection(key)) {
                continue;
            }
            target.set(key, source.get(key));
        }
    }

    private void syncBundledLanguageFile(Path file, String resourcePath) {
        if (Files.notExists(file)) {
            ensureBundledResource(file, resourcePath);
            return;
        }

        YamlConfiguration bundled = loadBundledConfiguration(resourcePath);
        if (bundled.getKeys(true).isEmpty()) {
            return;
        }

        YamlConfiguration existing = new YamlConfiguration();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            existing.load(reader);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load language file for sync " + file.getFileName(), exception);
            return;
        }

        boolean changed = false;
        for (String key : bundled.getKeys(true)) {
            if (bundled.isConfigurationSection(key) || existing.contains(key)) {
                continue;
            }
            existing.set(key, bundled.get(key));
            changed = true;
        }

        if (!changed) {
            return;
        }

        try {
            Files.writeString(file, existing.saveToString(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to write synced language file " + file.getFileName(), exception);
        }
    }
}
