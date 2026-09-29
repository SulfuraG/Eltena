package com.eltena.core.infrastructure.cache;

import com.eltena.core.domain.stats.PlayerStats;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerStatsCache {

    private final Map<UUID, PlayerStats> stats = new ConcurrentHashMap<>();

    public PlayerStats get(UUID playerId) {
        return stats.get(playerId);
    }

    public void put(PlayerStats value) {
        stats.put(value.playerId(), value);
    }

    public void invalidate(UUID playerId) {
        stats.remove(playerId);
    }
}
