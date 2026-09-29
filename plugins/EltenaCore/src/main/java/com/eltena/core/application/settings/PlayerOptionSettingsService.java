package com.eltena.core.application.settings;

import com.eltena.core.domain.player.PlayerOptionSettings;
import com.eltena.core.i18n.MessageService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerOptionSettingsService {

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final Map<UUID, PlayerOptionSettings> cache = new ConcurrentHashMap<>();

    public PlayerOptionSettingsService(JavaPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public PlayerOptionSettings load(UUID playerId) {
        if (playerId == null) {
            return defaults();
        }
        PlayerOptionSettings cached = cache.get(playerId);
        if (cached != null) {
            return cached;
        }
        Path file = settingsFile(playerId);
        PlayerOptionSettings loaded = defaults();
        if (Files.exists(file)) {
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.load(Files.newBufferedReader(file, StandardCharsets.UTF_8));
                loaded = new PlayerOptionSettings(
                    yaml.getBoolean("floating-damage", defaults().floatingDamage()),
                    yaml.getBoolean("chat-damage", defaults().chatDamage()),
                    yaml.getBoolean("exp-chat", defaults().expChat()),
                    yaml.getBoolean("dps-chat", defaults().dpsChat())
                );
            } catch (Exception exception) {
                plugin.getLogger().warning(
                    "[EltenaCore] プレイヤー設定の読み込みに失敗しました: "
                        + playerId + " message=" + exception.getMessage()
                );
            }
        }
        cache.put(playerId, loaded);
        return loaded;
    }

    public PlayerOptionSettings save(UUID playerId, PlayerOptionSettings settings) {
        if (playerId == null || settings == null) {
            return defaults();
        }
        Path file = settingsFile(playerId);
        try {
            Files.createDirectories(file.getParent());
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("floating-damage", settings.floatingDamage());
            yaml.set("chat-damage", settings.chatDamage());
            yaml.set("exp-chat", settings.expChat());
            yaml.set("dps-chat", settings.dpsChat());
            Files.writeString(file, yaml.saveToString(), StandardCharsets.UTF_8);
            cache.put(playerId, settings);
            return settings;
        } catch (IOException exception) {
            plugin.getLogger().warning(
                "[EltenaCore] プレイヤー設定の保存に失敗しました: "
                    + playerId + " message=" + exception.getMessage()
            );
            return load(playerId);
        }
    }

    public PlayerOptionSettings toggleFloatingDamage(UUID playerId) {
        PlayerOptionSettings current = load(playerId);
        return save(playerId, current.withFloatingDamage(!current.floatingDamage()));
    }

    public PlayerOptionSettings toggleExpChat(UUID playerId) {
        PlayerOptionSettings current = load(playerId);
        return save(playerId, current.withExpChat(!current.expChat()));
    }

    public PlayerOptionSettings toggleChatDamage(UUID playerId) {
        PlayerOptionSettings current = load(playerId);
        return save(playerId, current.withChatDamage(!current.chatDamage()));
    }

    public PlayerOptionSettings toggleDpsChat(UUID playerId) {
        PlayerOptionSettings current = load(playerId);
        return save(playerId, current.withDpsChat(!current.dpsChat()));
    }

    public boolean isFloatingDamageEnabled(UUID playerId) {
        return load(playerId).floatingDamage();
    }

    public boolean isExpChatEnabled(UUID playerId) {
        return load(playerId).expChat();
    }

    public boolean isChatDamageEnabled(UUID playerId) {
        return load(playerId).chatDamage();
    }

    public boolean isDpsChatEnabled(UUID playerId) {
        return load(playerId).dpsChat();
    }

    public void invalidate(UUID playerId) {
        if (playerId != null) {
            cache.remove(playerId);
        }
    }

    public PlayerOptionSettings resetToDefaults(UUID playerId) throws IOException {
        if (playerId == null) {
            return defaults();
        }
        Files.deleteIfExists(settingsFile(playerId));
        invalidate(playerId);
        return save(playerId, defaults());
    }

    private PlayerOptionSettings defaults() {
        return new PlayerOptionSettings(
            messages.getBoolean(
                "combat.floating-damage.enabled-default",
                plugin.getConfig().getBoolean("player-options.defaults.floating-damage", false)
            ),
            plugin.getConfig().getBoolean("player-options.defaults.chat-damage", false),
            plugin.getConfig().getBoolean("player-options.defaults.exp-chat", true),
            plugin.getConfig().getBoolean("player-options.defaults.dps-chat", false)
        );
    }

    private Path settingsFile(UUID playerId) {
        return plugin.getDataFolder().toPath()
            .resolve("players")
            .resolve(playerId.toString())
            .resolve("settings.yml");
    }
}
