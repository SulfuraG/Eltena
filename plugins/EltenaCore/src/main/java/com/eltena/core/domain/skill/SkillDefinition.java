package com.eltena.core.domain.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record SkillDefinition(
    String id,
    String displayName,
    String description,
    List<String> descriptionLines,
    String category,
    String icon,
    int requiredPoints,
    int requiredLevel,
    int maxRank,
    Position position,
    Requirements requirements,
    Map<String, Map<String, Object>> effects,
    List<String> unlockAbilities,
    List<String> prerequisites
) {
    public SkillDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(category, "category");
        descriptionLines = List.copyOf(Objects.requireNonNullElse(descriptionLines, List.of()));
        description = normalizeDescription(description, descriptionLines);
        icon = normalizeText(icon);
        requiredLevel = Math.max(0, requiredLevel);
        position = Objects.requireNonNullElse(position, new Position(0, 0));
        requirements = Objects.requireNonNullElse(requirements, Requirements.empty());
        effects = immutableEffects(effects);
        unlockAbilities = List.copyOf(Objects.requireNonNullElse(unlockAbilities, List.of()));
        prerequisites = List.copyOf(prerequisites != null ? prerequisites : requirements.flattenedPrerequisites());
    }

    private static String normalizeDescription(String fallback, List<String> lines) {
        if (!lines.isEmpty()) {
            return lines.stream()
                .filter(line -> line != null && !line.isBlank())
                .collect(Collectors.joining(" "));
        }
        return normalizeText(fallback);
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value;
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

    public record Position(int x, int y) {
    }

    public record Requirements(
        List<String> all,
        List<String> any,
        Map<String, Integer> ranks
    ) {
        public Requirements {
            all = List.copyOf(Objects.requireNonNullElse(all, List.of()));
            any = List.copyOf(Objects.requireNonNullElse(any, List.of()));
            ranks = Map.copyOf(Objects.requireNonNullElse(ranks, Map.of()));
        }

        public static Requirements empty() {
            return new Requirements(List.of(), List.of(), Map.of());
        }

        public boolean isEmpty() {
            return all.isEmpty() && any.isEmpty() && ranks.isEmpty();
        }

        public List<String> flattenedPrerequisites() {
            return Stream.concat(
                    Stream.concat(all.stream(), any.stream()),
                    ranks.keySet().stream()
                )
                .distinct()
                .toList();
        }
    }
}
