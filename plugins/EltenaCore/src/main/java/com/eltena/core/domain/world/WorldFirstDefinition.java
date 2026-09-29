package com.eltena.core.domain.world;

public record WorldFirstDefinition(
    String id,
    String displayName,
    String titleRewardId,
    long worldRankExperience
) {
}
