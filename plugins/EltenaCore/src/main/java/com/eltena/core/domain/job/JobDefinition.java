package com.eltena.core.domain.job;

public record JobDefinition(
    String id,
    String displayName,
    String description,
    JobTier tier
) {
}
