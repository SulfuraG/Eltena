package com.eltena.effectcore.sequence;

import java.util.Locale;

public enum SequenceStepType {
    EFFECT,
    SOUND,
    LIVE_SOUND,
    CAMERA;

    public static SequenceStepType fromConfig(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Sequence step type is required.");
        }
        String normalized = value.trim()
            .replace('-', '_')
            .toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "EFFECT" -> EFFECT;
            case "SOUND" -> SOUND;
            case "LIVE_SOUND" -> LIVE_SOUND;
            case "CAMERA" -> CAMERA;
            default -> throw new IllegalArgumentException("Unsupported sequence step type: " + value);
        };
    }
}
