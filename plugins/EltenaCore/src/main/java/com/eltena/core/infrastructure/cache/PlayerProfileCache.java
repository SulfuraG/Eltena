package com.eltena.core.infrastructure.cache;

import com.eltena.core.domain.player.PlayerProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerProfileCache {

    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();

    public PlayerProfile get(UUID playerId) {
        return profiles.get(playerId);
    }

    public void put(PlayerProfile profile) {
        profiles.put(profile.playerId(), profile);
    }

    public void invalidate(UUID playerId) {
        profiles.remove(playerId);
    }
}
