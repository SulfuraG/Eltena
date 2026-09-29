package com.eltena.core.domain.title;

import java.util.Locale;

public enum TitleCategory {
    ACHIEVEMENT("achievement", "功績"),
    EXPLORATION("exploration", "探検"),
    WORLD_FIRST("world_first", "世界初"),
    HONOR("honor", "名誉");

    private final String configKey;
    private final String displayName;

    TitleCategory(String configKey, String displayName) {
        this.configKey = configKey;
        this.displayName = displayName;
    }

    public String configKey() {
        return configKey;
    }

    public String displayName() {
        return displayName;
    }

    public static TitleCategory fromConfigValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return HONOR;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (TitleCategory category : values()) {
            if (category.configKey.equals(normalized)) {
                return category;
            }
        }
        return HONOR;
    }
}
