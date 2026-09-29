package com.eltena.sound.sound;

public record VanillaMusicControlConfig(
    boolean enabled,
    VanillaMusicControlMode mode,
    boolean stopOnPlay,
    boolean stopOnJoin,
    boolean restoreWhenNoEltenaBgm,
    int intervalTicks
) {
    public static VanillaMusicControlConfig defaults() {
        return new VanillaMusicControlConfig(
            true,
            VanillaMusicControlMode.ALWAYS_SUPPRESS,
            false,
            true,
            false,
            20
        );
    }

    public VanillaMusicControlConfig normalized() {
        return new VanillaMusicControlConfig(
            enabled,
            mode == null ? VanillaMusicControlMode.ALWAYS_SUPPRESS : mode,
            stopOnPlay,
            stopOnJoin,
            restoreWhenNoEltenaBgm,
            Math.max(1, intervalTicks)
        );
    }
}
