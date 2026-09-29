package com.eltena.core.domain.stats;

public record WeaponMasteryProgress(
    int level,
    long experience
) {

    public static WeaponMasteryProgress initial() {
        return new WeaponMasteryProgress(0, 0L);
    }
}
