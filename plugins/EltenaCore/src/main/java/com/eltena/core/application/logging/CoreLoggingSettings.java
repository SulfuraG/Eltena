package com.eltena.core.application.logging;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class CoreLoggingSettings {

    private static final String DEBUG_PROPERTY = "eltena.debug";

    private CoreLoggingSettings() {
    }

    public static boolean debugEnabled(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return systemDebugEnabled()
            || config.getBoolean("logging.debug", false);
    }

    public static boolean syncDebugEnabled(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return debugEnabled(plugin)
            || config.getBoolean("logging.sync-debug", false)
            || config.getBoolean("mod-integration.sync-debug", false);
    }

    public static boolean payloadDebugEnabled(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return syncDebugEnabled(plugin)
            || config.getBoolean("logging.payload-debug", false);
    }

    public static boolean renderDebugEnabled(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return debugEnabled(plugin)
            || config.getBoolean("logging.render-debug", false);
    }

    public static boolean profileDebugEnabled(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return debugEnabled(plugin)
            || config.getBoolean("logging.profile-debug", false);
    }

    public static boolean mmoItemsDebugEnabled(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return config.getBoolean("combat.mmoitems-debug.enabled", false);
    }

    public static boolean mmoItemsDefinitionLookupDebugEnabled(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return mmoItemsDebugEnabled(plugin)
            && config.getBoolean("combat.mmoitems-debug.definition-lookup", false);
    }

    public static boolean mmoItemsDefinitionStatsDebugEnabled(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return mmoItemsDebugEnabled(plugin)
            && config.getBoolean("combat.mmoitems-debug.definition-stats", false);
    }

    private static boolean systemDebugEnabled() {
        return Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
    }
}
