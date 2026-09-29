package com.eltena.core.domain.world;

import java.time.Instant;
import java.util.UUID;

public record WorldUniqueRecord(
    String id,
    UUID playerId,
    String playerName,
    Instant timestamp,
    UniqueRewardType rewardType,
    String rewardId
) {
}
