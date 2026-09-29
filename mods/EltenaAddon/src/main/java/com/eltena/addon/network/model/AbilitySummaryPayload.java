package com.eltena.addon.network.model;

import java.util.List;

public record AbilitySummaryPayload(
    String id,
    String displayName,
    List<String> description,
    String category,
    String icon,
    int cooldown,
    int mpCost,
    double cooldownRemaining
) {
}
