package com.eltena.addon.network.model;

import com.eltena.addon.network.SyncPayload;
import java.util.List;

/**
 * Addon-side view model for the skill_tree channel.
 * The UI keeps using mock nodes for now; this DTO exists so the screen can
 * switch to typed payload rendering later without changing authority boundaries.
 */
public record SkillTreePayload(
    String currentJobId,
    String currentJobName,
    int skillPoint,
    List<SkillNodePayload> nodes,
    List<String> unlockedTitleIds,
    List<String> unlockedTitleNames
) implements SyncPayload {
}
