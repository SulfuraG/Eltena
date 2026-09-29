package com.eltena.core.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;

public final class EltenaCorePlugin extends JavaPlugin {

    private ServiceRegistry services;
    private ModuleBootstrap bootstrap;

    @Override
    public void onLoad() {
        this.services = new ServiceRegistry(this);
        this.bootstrap = new ModuleBootstrap(services);
        bootstrap.load();
        getLogger().info("EltenaCore kernel loaded.");
    }

    @Override
    public void onEnable() {
        bootstrap.enable();
        getLogger().info(services.messages().get("plugin.enabled"));
    }

    @Override
    public void onDisable() {
        if (bootstrap != null) {
            bootstrap.disable();
        }
        getLogger().info(services.messages().get("plugin.disabled"));
    }

    public ServiceRegistry services() {
        return services;
    }
}
