package com.eltena.core.application.ability;

import com.eltena.core.domain.ability.AbilityDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AbilityCatalog {

    private final Map<String, AbilityDefinition> definitions = new LinkedHashMap<>();

    public synchronized void replaceAll(Map<String, AbilityDefinition> loaded) {
        definitions.clear();
        for (Map.Entry<String, AbilityDefinition> entry : loaded.entrySet()) {
            String normalizedId = normalizeId(entry.getKey());
            if (normalizedId != null && entry.getValue() != null) {
                definitions.put(normalizedId, entry.getValue());
            }
        }
    }

    public synchronized AbilityDefinition find(String id) {
        return definitions.get(normalizeId(id));
    }

    public synchronized List<AbilityDefinition> list() {
        return List.copyOf(definitions.values());
    }

    public static String normalizeId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }
}
