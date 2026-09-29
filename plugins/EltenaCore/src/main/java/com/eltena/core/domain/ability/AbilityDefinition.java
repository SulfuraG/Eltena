package com.eltena.core.domain.ability;

import com.eltena.core.application.ability.AbilityCatalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public record AbilityDefinition(
    String id,
    String displayName,
    String description,
    List<String> descriptionLines,
    String category,
    String icon,
    int cooldownSeconds,
    Cost cost,
    List<String> weaponTypes,
    Requirements requires,
    Map<String, Map<String, Object>> effects,
    Executor executor
) {
    public AbilityDefinition {
        id = Objects.requireNonNull(AbilityCatalog.normalizeId(id), "id");
        Objects.requireNonNull(displayName, "displayName");
        category = category == null ? "general" : category;
        icon = icon == null ? "" : icon;
        descriptionLines = List.copyOf(Objects.requireNonNullElse(descriptionLines, List.of()));
        description = normalizeDescription(description, descriptionLines);
        cooldownSeconds = Math.max(0, cooldownSeconds);
        cost = Objects.requireNonNullElse(cost, Cost.empty());
        weaponTypes = List.copyOf(Objects.requireNonNullElse(weaponTypes, List.of("any")));
        requires = Objects.requireNonNullElse(requires, Requirements.empty());
        effects = immutableEffects(effects);
        executor = Objects.requireNonNullElse(executor, Executor.empty());
    }

    private static String normalizeDescription(String fallback, List<String> lines) {
        if (!lines.isEmpty()) {
            return lines.stream()
                .filter(line -> line != null && !line.isBlank())
                .collect(Collectors.joining(" "));
        }
        return fallback == null ? "" : fallback;
    }

    private static Map<String, Map<String, Object>> immutableEffects(Map<String, Map<String, Object>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Map<String, Object>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, value == null ? Map.of() : Map.copyOf(value)));
        return Map.copyOf(result);
    }

    public Map<String, Object> effect(String effectId) {
        if (effectId == null || effectId.isBlank()) {
            return Map.of();
        }
        return effects.getOrDefault(effectId, Map.of());
    }

    public record Cost(int mp) {
        public Cost {
            mp = Math.max(0, mp);
        }

        public static Cost empty() {
            return new Cost(0);
        }
    }

    public record Requirements(List<String> skills) {
        public Requirements {
            List<String> source = Objects.requireNonNullElse(skills, List.of());
            skills = List.copyOf(source.stream()
                .filter(Objects::nonNull)
                .map(value -> AbilityCatalog.normalizeId(value))
                .filter(Objects::nonNull)
                .toList());
        }

        public static Requirements empty() {
            return new Requirements(List.of());
        }
    }

    public record Executor(String type, String skill, String action, Map<String, Object> meta) {
        public Executor {
            type = type == null ? "" : type.toLowerCase(Locale.ROOT);
            skill = skill == null ? "" : skill;
            action = action == null ? "" : action.toLowerCase(Locale.ROOT);
            meta = Map.copyOf(Objects.requireNonNullElse(meta, Map.of()));
        }

        public static Executor empty() {
            return new Executor("", "", "", Map.of());
        }
    }
}
