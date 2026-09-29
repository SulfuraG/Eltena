package com.eltena.core.application.equipment;

import com.eltena.core.domain.player.PlayerStatBonuses;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record MmoItemsDefinitionBonuses(
    double defense,
    Set<String> mappedItemIds
) {

    public MmoItemsDefinitionBonuses {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (mappedItemIds != null) {
            for (String itemId : mappedItemIds) {
                String normalizedId = Objects.requireNonNullElse(itemId, "").trim().toLowerCase();
                if (!normalizedId.isBlank()) {
                    normalized.add(normalizedId);
                }
            }
        }
        mappedItemIds = Set.copyOf(normalized);
    }

    public static MmoItemsDefinitionBonuses none() {
        return new MmoItemsDefinitionBonuses(0.0D, Set.of());
    }

    public PlayerStatBonuses toPlayerStatBonuses() {
        return new PlayerStatBonuses(0.0D, 0.0D, 0.0D, 0.0D, defense, 0.0D, 0.0D);
    }

    public boolean hasMappings() {
        return !mappedItemIds.isEmpty();
    }

    public String summary() {
        List<String> entries = new ArrayList<>(1);
        append(entries, "def", defense);
        return entries.isEmpty() ? "none" : String.join(" ", entries);
    }

    private void append(List<String> entries, String key, double value) {
        if (Math.abs(value) < 0.000001D) {
            return;
        }
        entries.add(key + ":" + (value >= 0.0D ? "+" : "") + value);
    }
}
