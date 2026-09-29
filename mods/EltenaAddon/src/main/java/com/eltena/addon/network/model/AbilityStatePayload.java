package com.eltena.addon.network.model;

import com.eltena.addon.network.SyncPayload;
import java.util.List;

public record AbilityStatePayload(
    List<AbilitySummaryPayload> unlockedAbilities,
    List<EquippedAbilitySlotPayload> equippedSlots,
    List<AbilityRadialSlotPayload> radialSlots,
    int totalAbilities
) implements SyncPayload {
}
