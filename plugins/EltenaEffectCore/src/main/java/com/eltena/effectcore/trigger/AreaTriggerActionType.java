package com.eltena.effectcore.trigger;

public enum AreaTriggerActionType {
    EFFECT,
    SEQUENCE;

    public static AreaTriggerActionType fromConfig(String value, String sourceDescription) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Missing trigger action type at " + sourceDescription
            );
        }
        try {
            return AreaTriggerActionType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                "Unsupported trigger action type at " + sourceDescription + ": " + value,
                exception
            );
        }
    }
}
