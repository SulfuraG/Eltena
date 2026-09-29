package com.eltena.core.domain.job;

public enum JobTier {
    BASIC("下位"),
    ADVANCED("上位"),
    RARE("希少");

    private final String displayName;

    JobTier(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
