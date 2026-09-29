package com.eltena.core.application.progression;

import com.eltena.core.domain.player.PlayerProfile;
import com.eltena.core.domain.stats.PlayerStats;

import java.util.List;

public record ProgressionResult(
    PlayerProfile profile,
    PlayerStats stats,
    List<String> messages,
    long gainedExperience,
    int previousLevel,
    int currentLevel
) {
    public ProgressionResult(PlayerProfile profile, PlayerStats stats, List<String> messages) {
        this(profile, stats, messages, 0L, 0, 0);
    }

    public boolean leveledUp() {
        return currentLevel > previousLevel;
    }
}
