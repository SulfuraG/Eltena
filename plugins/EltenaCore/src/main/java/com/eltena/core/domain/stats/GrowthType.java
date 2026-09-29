package com.eltena.core.domain.stats;

public enum GrowthType {
    ATTACK("attack"),
    MP("mp"),
    DEFENSE("defense");

    private final String key;

    GrowthType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
