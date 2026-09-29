package com.eltena.soundcore.config;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class LanguageService {
    private final JavaPlugin plugin;
    private Map<String, String> messages = Map.of();
    private String locale = "ja_jp";

    public LanguageService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        String configuredLocale = plugin.getConfig().getString("default-language", "ja_jp");
        this.locale = configuredLocale == null || configuredLocale.isBlank()
            ? "ja_jp"
            : configuredLocale.trim().toLowerCase();

        LinkedHashMap<String, String> loaded = new LinkedHashMap<>(readLanguage("en_us"));
        if (!"en_us".equals(this.locale)) {
            loaded.putAll(readLanguage(this.locale));
        }
        this.messages = Collections.unmodifiableMap(loaded);
    }

    public String locale() {
        return locale;
    }

    public String prefixed(String key, Map<String, String> placeholders) {
        return colorize(resolve("meta.prefix", Map.of()) + resolve(key, placeholders));
    }

    public String text(String key, Map<String, String> placeholders) {
        return colorize(resolve(key, placeholders));
    }

    public String plain(String key, Map<String, String> placeholders) {
        return stripColor(text(key, placeholders));
    }

    private Map<String, String> readLanguage(String localeKey) {
        File file = new File(plugin.getDataFolder(), "language/" + localeKey + ".yml");
        if (!file.isFile()) {
            throw new IllegalStateException("Language file is missing: " + file.getAbsolutePath());
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        flatten("", yaml, values);
        return values;
    }

    private void flatten(String prefix, ConfigurationSection section, Map<String, String> values) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isBlank() ? key : prefix + "." + key;
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) {
                flatten(path, child, values);
            } else if (value != null) {
                values.put(path, String.valueOf(value));
            }
        }
    }

    private String resolve(String key, Map<String, String> placeholders) {
        String resolved = messages.getOrDefault(key, key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return resolved;
    }

    private String colorize(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    private String stripColor(String value) {
        String stripped = ChatColor.stripColor(value);
        return stripped == null ? value : stripped;
    }
}
