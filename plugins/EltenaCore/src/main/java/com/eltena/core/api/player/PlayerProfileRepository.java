package com.eltena.core.api.player;

import com.eltena.core.domain.player.PlayerProfile;

import java.io.IOException;
import java.util.UUID;

public interface PlayerProfileRepository {

    PlayerProfile loadOrCreate(UUID playerId, String playerName) throws IOException;

    PlayerProfile save(PlayerProfile profile) throws IOException;

    void invalidate(UUID playerId);
}
