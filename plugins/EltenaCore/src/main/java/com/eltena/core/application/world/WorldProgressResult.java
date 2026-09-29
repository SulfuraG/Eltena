package com.eltena.core.application.world;

import com.eltena.core.domain.player.PlayerProfile;

import java.util.List;

public record WorldProgressResult(
    PlayerProfile profile,
    List<String> messages
) {
}
