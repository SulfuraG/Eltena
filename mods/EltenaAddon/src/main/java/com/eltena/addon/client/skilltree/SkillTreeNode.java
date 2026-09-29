package com.eltena.addon.client.skilltree;

import java.util.List;

public record SkillTreeNode(
    String id,
    String name,
    String description,
    int canvasX,
    int canvasY,
    int requiredPoints,
    int currentRank,
    int maxRank,
    SkillNodeState state,
    List<String> prerequisites
) {
}
