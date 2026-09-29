package com.eltena.core.application.progression;

import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.stats.GrowthType;
import com.eltena.core.domain.stats.PlayerStats;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public final class StatGrowthSystem {

    private final ServiceRegistry services;

    public StatGrowthSystem(ServiceRegistry services) {
        this.services = services;
    }

    public ProgressionResult addGrowth(UUID playerId, GrowthType type, long amount) throws IOException {
        if (amount <= 0L) {
            throw new IllegalArgumentException(services.messages().get("growth.stat.invalid-amount"));
        }

        PlayerStats stats = services.playerStats().loadOrCreate(playerId);
        return new ProgressionResult(
            null,
            stats,
            List.of(services.messages().get("growth.stat.legacy-disabled", "type", typeLabel(type)))
        );
    }

    private String typeLabel(GrowthType type) {
        return switch (type) {
            case ATTACK -> services.messages().get("growth.type.attack");
            case MP -> services.messages().get("growth.type.mp");
            case DEFENSE -> services.messages().get("growth.type.defense");
        };
    }
}
