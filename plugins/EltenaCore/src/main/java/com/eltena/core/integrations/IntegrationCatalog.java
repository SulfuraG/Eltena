package com.eltena.core.integrations;

import org.bukkit.plugin.PluginManager;

public final class IntegrationCatalog {

    private boolean placeholderApiEnabled;
    private boolean skriptEnabled;
    private boolean mythicMobsEnabled;
    private boolean modHooksReady;

    private IntegrationCatalog() {
    }

    public static IntegrationCatalog detect(PluginManager pluginManager) {
        return new IntegrationCatalog().refresh(pluginManager);
    }

    public IntegrationCatalog refresh(PluginManager pluginManager) {
        this.placeholderApiEnabled = pluginManager.isPluginEnabled("PlaceholderAPI");
        this.skriptEnabled = pluginManager.isPluginEnabled("Skript");
        this.mythicMobsEnabled = pluginManager.isPluginEnabled("MythicMobs");
        this.modHooksReady = false;
        return this;
    }

    public boolean placeholderApiEnabled() {
        return placeholderApiEnabled;
    }

    public boolean skriptEnabled() {
        return skriptEnabled;
    }

    public boolean mythicMobsEnabled() {
        return mythicMobsEnabled;
    }

    public boolean modHooksReady() {
        return modHooksReady;
    }
}
