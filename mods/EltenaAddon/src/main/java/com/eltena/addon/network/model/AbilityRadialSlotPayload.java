package com.eltena.addon.network.model;

public record AbilityRadialSlotPayload(
    int slot,
    String directionLabel,
    String abilityId,
    String displayName,
    String icon,
    String category,
    int cooldown,
    double cooldownRemaining
) {
}
