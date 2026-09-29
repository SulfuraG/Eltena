package com.eltena.core.domain.stats;

public record StatGrowthProgress(
    int level,
    long experience
) {

    public static StatGrowthProgress initial() {
        return new StatGrowthProgress(0, 0L);
    }
}
