package com.eltena.core.application.world;

import com.eltena.core.domain.world.WorldRankDefinition;

import java.util.List;

public final class WorldRankCatalog {

    private final List<WorldRankDefinition> ranks = List.of(
        new WorldRankDefinition("unranked", "無名", 0),
        new WorldRankDefinition("rising_star", "新鋭", 20),
        new WorldRankDefinition("explorer_rank", "探索者", 50),
        new WorldRankDefinition("hero_candidate", "英雄候補", 100),
        new WorldRankDefinition("hero", "英雄", 180),
        new WorldRankDefinition("legend", "伝説", 280),
        new WorldRankDefinition("mythic", "神話級", 400)
    );

    public List<WorldRankDefinition> list() {
        return ranks;
    }

    public WorldRankDefinition resolve(long experience) {
        WorldRankDefinition resolved = ranks.getFirst();
        for (WorldRankDefinition rank : ranks) {
            if (experience >= rank.requiredExperience()) {
                resolved = rank;
            }
        }
        return resolved;
    }

    public WorldRankDefinition find(String id) {
        return ranks.stream()
            .filter(rank -> rank.id().equalsIgnoreCase(id))
            .findFirst()
            .orElse(null);
    }
}
