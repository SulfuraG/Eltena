package com.eltena.core.application.equipment;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class MmoItemsDefinitionMappingRepository {

    private static final String FILE_NAME = "mmoitems-mappings.yml";
    private static final String ROOT_KEY = "mmoitems-mappings";

    private final JavaPlugin plugin;
    private final Map<String, MmoItemsDefinitionReference> byModItemId = new LinkedHashMap<>();

    public MmoItemsDefinitionMappingRepository(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public int reload() {
        ensureResource();
        byModItemId.clear();

        File file = new File(plugin.getDataFolder(), FILE_NAME);
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file);
            ConfigurationSection root = yaml.getConfigurationSection(ROOT_KEY);
            if (root == null) {
                plugin.getLogger().warning("[EltenaCore] " + FILE_NAME + " is missing root key '" + ROOT_KEY + "'.");
                return 0;
            }

            for (String modItemId : root.getKeys(false)) {
                String path = ROOT_KEY + "." + modItemId;
                MmoItemsDefinitionReference reference = new MmoItemsDefinitionReference(
                    yaml.getString(path + ".type", ""),
                    yaml.getString(path + ".id", "")
                );
                if (!reference.isValid()) {
                    plugin.getLogger().warning(
                        "[EltenaCore] Ignoring invalid MMOItems mapping for '" + modItemId + "'."
                    );
                    continue;
                }
                byModItemId.put(normalizeKey(modItemId), reference);
            }
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning(
                "[EltenaCore] Failed to load " + FILE_NAME + ": " + exception.getMessage()
            );
        }
        return byModItemId.size();
    }

    public MmoItemsDefinitionReference find(String modItemId) {
        if (modItemId == null) {
            return null;
        }
        return byModItemId.get(normalizeKey(modItemId));
    }

    public int size() {
        return byModItemId.size();
    }

    private void ensureResource() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }
    }

    private String normalizeKey(String modItemId) {
        return Objects.requireNonNullElse(modItemId, "").trim().toLowerCase(Locale.ROOT);
    }
}
