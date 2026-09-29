package com.eltena.soundcore.sound;

import java.util.Locale;

public enum SoundCategory {
    BGM,
    SYSTEM,
    VOICE;

    public static SoundCategory fromConfig(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Sound category is required.");
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "BGM" -> BGM;
            case "SYSTEM" -> SYSTEM;
            case "VOICE" -> VOICE;
            default -> throw new IllegalArgumentException("Unsupported sound category: " + value);
        };
    }
}
