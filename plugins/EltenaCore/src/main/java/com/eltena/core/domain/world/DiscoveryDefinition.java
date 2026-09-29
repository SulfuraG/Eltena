package com.eltena.core.domain.world;

public record DiscoveryDefinition(
    String id,
    String displayName,
    long worldRankExperience,
    String titleRewardId,
    String firstDiscoveryId
) {
}
