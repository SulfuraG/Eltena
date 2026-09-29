package com.eltena.core.domain.world;

import java.util.Locale;

public enum UniqueRewardType {
    NONE("none"),
    TITLE("title"),
    JOB("job");

    private final String configKey;

    UniqueRewardType(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static UniqueRewardType fromConfigValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (UniqueRewardType type : values()) {
            if (type.configKey.equals(normalized)) {
                return type;
            }
        }
        return NONE;
    }
}
