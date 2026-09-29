package com.eltena.soundcore.sound;

import java.util.Locale;

public enum SoundType {
    BGM,
    SE;

    public static SoundType fromConfig(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Sound type is required.");
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "BGM" -> BGM;
            case "SE" -> SE;
            default -> throw new IllegalArgumentException("Unsupported sound type: " + value);
        };
    }
}
