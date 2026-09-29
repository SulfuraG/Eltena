package com.eltena.core.application.world;

import com.eltena.core.domain.world.DiscoveryDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DiscoveryCatalog {

    private final Map<String, DiscoveryDefinition> discoveries = new LinkedHashMap<>();

    public synchronized void replaceAll(Map<String, DiscoveryDefinition> updated) {
        discoveries.clear();
        discoveries.putAll(updated);
    }

    public synchronized DiscoveryDefinition find(String id) {
        return discoveries.get(id);
    }

    public synchronized List<DiscoveryDefinition> list() {
        return List.copyOf(discoveries.values());
    }
}
