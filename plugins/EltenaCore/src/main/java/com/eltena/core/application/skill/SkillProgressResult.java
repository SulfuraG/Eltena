package com.eltena.core.application.skill;

import com.eltena.core.domain.player.PlayerProfile;

import java.util.List;

public record SkillProgressResult(
    PlayerProfile profile,
    List<String> messages,
    boolean progressed,
    boolean rankAdvanced,
    String denialReason,
    String skillId,
    int rank,
    int maxRank
) {
    public boolean denied() {
        return denialReason != null && !denialReason.isBlank();
    }
}
