package com.eltena.effectcore.trigger;

public enum AreaTriggerType {
    BOX_ENTER;

    public static AreaTriggerType fromConfig(String value, String sourceDescription) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Missing trigger type at " + sourceDescription
            );
        }
        try {
            return AreaTriggerType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                "Unsupported trigger type at " + sourceDescription + ": " + value,
                exception
            );
        }
    }
}
