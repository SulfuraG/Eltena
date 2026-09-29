package com.eltena.core.domain.world;

import java.time.Instant;
import java.util.UUID;

public record WorldFirstRecord(
    String id,
    UUID playerId,
    String playerName,
    Instant timestamp,
    String titleRewardId
) {
}
