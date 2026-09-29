package com.eltena.effect.effect;

import java.util.Locale;

public enum ScreenShaderDirection {
    OUTWARD,
    INWARD,
    LEFT,
    RIGHT,
    UP,
    DOWN,
    DIAGONAL_LEFT,
    DIAGONAL_RIGHT;

    public static ScreenShaderDirection fromPayload(String value) {
        if (value == null || value.isBlank()) {
            return OUTWARD;
        }

        String normalized = value.trim()
            .toUpperCase(Locale.ROOT)
            .replace('-', '_');
        try {
            return ScreenShaderDirection.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return OUTWARD;
        }
    }

    public int uniformCode() {
        return switch (this) {
            case OUTWARD -> 0;
            case INWARD -> 1;
            case LEFT -> 2;
            case RIGHT -> 3;
            case UP -> 4;
            case DOWN -> 5;
            case DIAGONAL_LEFT -> 6;
            case DIAGONAL_RIGHT -> 7;
        };
    }
}
