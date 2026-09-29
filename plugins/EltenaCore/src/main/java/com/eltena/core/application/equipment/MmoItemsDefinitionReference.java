package com.eltena.core.application.equipment;

import java.util.Locale;
import java.util.Objects;

public record MmoItemsDefinitionReference(
    String type,
    String id
) {

    public MmoItemsDefinitionReference {
        type = normalize(type);
        id = normalize(id);
    }

    public boolean isValid() {
        return !type.isBlank() && !id.isBlank();
    }

    public String displayKey() {
        if (!isValid()) {
            return "invalid";
        }
        return type + "." + id;
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim().toUpperCase(Locale.ROOT);
    }
}
