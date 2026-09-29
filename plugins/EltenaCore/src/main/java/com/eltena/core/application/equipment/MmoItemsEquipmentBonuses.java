package com.eltena.core.application.equipment;

import com.eltena.core.domain.player.PlayerStatBonuses;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record MmoItemsEquipmentBonuses(
    double weaponDamage,
    double defense,
    double maxHealthBonus,
    Set<String> resolvedItemIds
) {

    public MmoItemsEquipmentBonuses {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (resolvedItemIds != null) {
            for (String itemId : resolvedItemIds) {
                String normalizedId = Objects.requireNonNullElse(itemId, "").trim().toLowerCase();
                if (!normalizedId.isBlank()) {
                    normalized.add(normalizedId);
                }
            }
        }
        resolvedItemIds = Set.copyOf(normalized);
    }

    public static MmoItemsEquipmentBonuses none() {
        return new MmoItemsEquipmentBonuses(0.0D, 0.0D, 0.0D, Set.of());
    }

    public PlayerStatBonuses toPlayerStatBonuses() {
        return new PlayerStatBonuses(0.0D, 0.0D, 0.0D, 0.0D, defense, 0.0D, 0.0D);
    }

    public MmoItemsEquipmentBonuses add(MmoItemsEquipmentBonuses other) {
        if (other == null) {
            return this;
        }
        LinkedHashSet<String> mergedIds = new LinkedHashSet<>(resolvedItemIds);
        mergedIds.addAll(other.resolvedItemIds());
        return new MmoItemsEquipmentBonuses(
            weaponDamage + other.weaponDamage(),
            defense + other.defense(),
            maxHealthBonus + other.maxHealthBonus(),
            mergedIds
        );
    }

    public boolean isEmpty() {
        return resolvedItemIds.isEmpty()
            && Math.abs(weaponDamage) < 0.000001D
            && Math.abs(defense) < 0.000001D
            && Math.abs(maxHealthBonus) < 0.000001D;
    }

    public String summary() {
        List<String> entries = new ArrayList<>(4);
        append(entries, "weaponAttack", weaponDamage);
        append(entries, "def", defense);
        append(entries, "maxHp", maxHealthBonus);
        return entries.isEmpty() ? "none" : String.join(" ", entries);
    }

    private void append(List<String> entries, String key, double value) {
        if (Math.abs(value) < 0.000001D) {
            return;
        }
        entries.add(key + ":" + (value >= 0.0D ? "+" : "") + value);
    }
}
