package com.eltena.soundcore.config;

import org.bukkit.configuration.file.FileConfiguration;

public record VanillaMusicControlSettings(
    boolean enabled,
    String mode,
    boolean stopOnPlay,
    boolean stopOnJoin,
    boolean restoreWhenNoEltenaBgm,
    int intervalTicks
) {
    public static final String MODE_ALWAYS_SUPPRESS = "ALWAYS_SUPPRESS";
    public static final String MODE_SUPPRESS_WHILE_ELTENA_BGM = "SUPPRESS_WHILE_ELTENA_BGM";
    private static final String DEFAULT_MODE = MODE_ALWAYS_SUPPRESS;

    public static VanillaMusicControlSettings fromConfig(FileConfiguration config) {
        String configuredMode = config.getString(
            "vanilla-music-control.mode",
            DEFAULT_MODE
        );
        String normalizedMode = configuredMode == null || configuredMode.isBlank()
            ? DEFAULT_MODE
            : configuredMode.trim().toUpperCase();
        return new VanillaMusicControlSettings(
            config.getBoolean("vanilla-music-control.enabled", true),
            normalizedMode,
            config.getBoolean("vanilla-music-control.stop-on-play", true),
            config.getBoolean("vanilla-music-control.stop-on-join", true),
            config.getBoolean("vanilla-music-control.restore-when-no-eltena-bgm", false),
            Math.max(1, config.getInt("vanilla-music-control.interval-ticks", 20))
        );
    }
}
