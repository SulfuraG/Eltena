package com.eltena.core.domain.world;

public record WorldUniqueDefinition(
    String id,
    String displayName,
    UniqueRewardType rewardType,
    String rewardId,
    long worldRankExperience
) {
}
