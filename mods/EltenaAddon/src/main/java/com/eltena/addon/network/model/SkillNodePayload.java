package com.eltena.addon.network.model;

import java.util.List;

/**
 * Provisional node-shaped DTO for future real skill tree rendering.
 * The current plugin payload does not emit full skill nodes yet, so this model
 * is prepared for the handoff from mock UI to synced UI.
 */
public record SkillNodePayload(
    String id,
    String displayName,
    List<String> description,
    String category,
    String icon,
    int currentRank,
    int maxRank,
    int requiredPoints,
    int requiredLevel,
    String state,
    List<String> prerequisites,
    List<String> requiresDisplay,
    int positionX,
    int positionY,
    List<String> effectsDisplay,
    List<String> unlockAbilitiesDisplay
) {
}
