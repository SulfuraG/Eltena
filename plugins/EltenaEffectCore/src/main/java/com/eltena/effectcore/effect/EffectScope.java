package com.eltena.effectcore.effect;

import java.util.Locale;

public enum EffectScope {
    WORLD_RADIUS;

    public static EffectScope fromConfig(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Effect scope is required.");
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "WORLD_RADIUS" -> WORLD_RADIUS;
            default -> throw new IllegalArgumentException("Unsupported effect scope: " + value);
        };
    }
}
