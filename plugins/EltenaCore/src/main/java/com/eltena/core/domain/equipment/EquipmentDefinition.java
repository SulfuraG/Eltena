package com.eltena.core.domain.equipment;

import com.eltena.core.domain.player.PlayerStatBonuses;

import java.util.Objects;

public record EquipmentDefinition(
    String id,
    String itemId,
    PlayerStatBonuses bonuses
) {

    public EquipmentDefinition {
        id = Objects.requireNonNullElse(id, "");
        itemId = normalizeItemId(itemId);
        bonuses = bonuses == null ? PlayerStatBonuses.none() : bonuses;
    }

    private static String normalizeItemId(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase();
    }
}
