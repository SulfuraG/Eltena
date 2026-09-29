package com.eltena.core.integrations.placeholder;

import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.integrations.IntegrationCatalog;

public final class EltenaCorePlaceholderBridge {

    private final ServiceRegistry services;
    private EltenaCorePlaceholderExpansion expansion;

    public EltenaCorePlaceholderBridge(ServiceRegistry services) {
        this.services = services;
    }

    public void registerIfAvailable() {
        IntegrationCatalog catalog = services.require(IntegrationCatalog.class);
        if (!catalog.placeholderApiEnabled()) {
            services.plugin().getLogger().info(services.messages().get("placeholder.skipped"));
            return;
        }

        if (expansion == null) {
            expansion = new EltenaCorePlaceholderExpansion(services);
            expansion.register();
            services.plugin().getLogger().info(services.messages().get("placeholder.registered"));
        }
    }

    public void unregisterIfRegistered() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
            services.plugin().getLogger().info(services.messages().get("placeholder.unregistered"));
        }
    }

    public boolean isRegistered() {
        return expansion != null;
    }
}
