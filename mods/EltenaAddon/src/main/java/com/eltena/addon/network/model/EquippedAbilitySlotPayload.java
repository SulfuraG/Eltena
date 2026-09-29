package com.eltena.addon.network.model;

public record EquippedAbilitySlotPayload(
    int slot,
    String keyName,
    String abilityId,
    String displayName
) {
}
