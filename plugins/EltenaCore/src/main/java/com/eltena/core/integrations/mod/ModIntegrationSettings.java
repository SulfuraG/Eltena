package com.eltena.core.integrations.mod;

import com.eltena.core.application.logging.CoreLoggingSettings;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class ModIntegrationSettings {

    private final JavaPlugin plugin;

    private boolean enabled;
    private boolean requireClientMod;
    private boolean syncDebug;
    private int protocolVersion;
    private boolean allowUiSync;
    private boolean allowSkillSync;

    public ModIntegrationSettings(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("mod-integration.enabled", true);
        this.requireClientMod = config.getBoolean("mod-integration.require-client-mod", false);
        this.syncDebug = CoreLoggingSettings.syncDebugEnabled(plugin);
        this.protocolVersion = Math.max(1, config.getInt("mod-integration.protocol-version", 1));
        this.allowUiSync = config.getBoolean("mod-integration.allow-ui-sync", true);
        this.allowSkillSync = config.getBoolean("mod-integration.allow-skill-sync", true);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean requireClientMod() {
        return requireClientMod;
    }

    public boolean syncDebug() {
        return syncDebug;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public boolean allowUiSync() {
        return allowUiSync;
    }

    public boolean allowSkillSync() {
        return allowSkillSync;
    }

    public boolean channelEnabled(ModSyncChannel channel) {
        return switch (channel) {
            case PLAYER_STATE, ABILITY_STATE, NOTIFICATION -> enabled && allowUiSync;
            case SKILL_TREE -> enabled && allowSkillSync;
        };
    }
}
