package com.eltena.sound.sound;

import java.util.Locale;

public enum VanillaMusicControlMode {
    ALWAYS_SUPPRESS,
    SUPPRESS_WHILE_ELTENA_BGM;

    public static VanillaMusicControlMode fromPayload(String value) {
        if (value == null || value.isBlank()) {
            return ALWAYS_SUPPRESS;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "ALWAYS_SUPPRESS" -> ALWAYS_SUPPRESS;
            case "SUPPRESS_WHILE_ELTENA_BGM" -> SUPPRESS_WHILE_ELTENA_BGM;
            default -> ALWAYS_SUPPRESS;
        };
    }
}
