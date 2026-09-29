package com.eltena.core.application.equipment;

import com.eltena.core.domain.equipment.EquipmentDefinition;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EquipmentCatalog {

    private final Map<String, EquipmentDefinition> byId = new LinkedHashMap<>();
    private final Map<String, EquipmentDefinition> byItemId = new LinkedHashMap<>();

    public void replaceAll(Map<String, EquipmentDefinition> definitions) {
        byId.clear();
        byItemId.clear();
        if (definitions == null || definitions.isEmpty()) {
            return;
        }
        for (EquipmentDefinition definition : definitions.values()) {
            if (definition == null || definition.id().isBlank()) {
                continue;
            }
            byId.put(definition.id(), definition);
            if (!definition.itemId().isBlank()) {
                byItemId.put(definition.itemId(), definition);
            }
        }
    }

    public EquipmentDefinition findByItemId(String itemId) {
        if (itemId == null) {
            return null;
        }
        return byItemId.get(itemId.trim().toLowerCase());
    }

    public Collection<EquipmentDefinition> all() {
        return List.copyOf(byId.values());
    }
}
