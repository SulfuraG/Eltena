package com.eltena.core.api.stats;

import com.eltena.core.domain.stats.PlayerStats;

import java.io.IOException;
import java.util.UUID;

public interface PlayerStatsRepository {

    PlayerStats loadOrCreate(UUID playerId) throws IOException;

    PlayerStats save(PlayerStats stats) throws IOException;

    void invalidate(UUID playerId);
}
