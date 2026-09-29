package com.eltena.core.domain.world;

public record WorldRankDefinition(
    String id,
    String displayName,
    long requiredExperience
) {
}
