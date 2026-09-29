package com.eltena.core.domain.title;

import java.util.LinkedHashMap;
import java.util.Map;

public record TitleDefinition(
    String id,
    String displayName,
    String description,
    TitleCategory category,
    Map<String, Map<String, Object>> effects
) {
    public TitleDefinition {
        effects = immutableEffects(effects);
    }

    public Map<String, Object> effect(String effectId) {
        if (effectId == null || effectId.isBlank()) {
            return Map.of();
        }
        return effects.getOrDefault(effectId, Map.of());
    }

    private static Map<String, Map<String, Object>> immutableEffects(Map<String, Map<String, Object>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Map<String, Object>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, value == null ? Map.of() : Map.copyOf(value)));
        return Map.copyOf(result);
    }
}
