package com.eltena.effectcore.effect;

public record RepeatSettings(
    boolean enabled,
    long intervalMs,
    int count,
    double powerMultiplier
) {
    public static RepeatSettings disabled() {
        return new RepeatSettings(false, 0L, 0, 1.0D);
    }
}
