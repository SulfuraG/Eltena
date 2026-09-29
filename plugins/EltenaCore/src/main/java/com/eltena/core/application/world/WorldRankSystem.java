package com.eltena.core.application.world;

import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.player.PlayerProfile;
import com.eltena.core.domain.world.WorldRankDefinition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class WorldRankSystem {

    private final ServiceRegistry services;
    private final WorldRankCatalog catalog;

    public WorldRankSystem(ServiceRegistry services, WorldRankCatalog catalog) {
        this.services = Objects.requireNonNull(services, "services");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public List<WorldRankDefinition> listRanks() {
        return catalog.list();
    }

    public String displayName(String rankId) {
        WorldRankDefinition definition = catalog.find(rankId);
        return definition == null ? rankId : definition.displayName();
    }

    public WorldProgressResult addExperience(UUID playerId, String playerName, long amount, String reason) throws IOException {
        PlayerProfile profile = services.playerProfiles().loadOrCreate(playerId, playerName);
        WorldProgressResult result = applyExperience(profile, amount, reason);
        services.playerProfiles().save(result.profile());
        return result;
    }

    public WorldProgressResult applyExperience(PlayerProfile profile, long amount, String reason) {
        if (amount <= 0) {
            return new WorldProgressResult(profile, List.of());
        }

        long updatedExperience = profile.worldRankExperience() + amount;
        WorldRankDefinition previousRank = catalog.resolve(profile.worldRankExperience());
        WorldRankDefinition nextRank = catalog.resolve(updatedExperience);

        PlayerProfile updated = profile.withWorldRank(nextRank.id(), updatedExperience);
        List<String> messages = new ArrayList<>();
        messages.add("世界階位経験値を " + amount + " 加算しました。");
        if (reason != null && !reason.isBlank()) {
            messages.add("加算理由: " + reason);
        }
        if (!previousRank.id().equals(nextRank.id())) {
            messages.add("世界階位が上昇しました。");
            messages.add("現在の世界階位: " + nextRank.displayName());
        }
        return new WorldProgressResult(updated, List.copyOf(messages));
    }
}
