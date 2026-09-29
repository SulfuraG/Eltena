package com.eltena.core.application.progression;

import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.stats.PlayerStats;
import com.eltena.core.domain.stats.WeaponType;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public final class WeaponMasterySystem {

    private final ServiceRegistry services;

    public WeaponMasterySystem(ServiceRegistry services) {
        this.services = services;
    }

    public ProgressionResult addMastery(UUID playerId, WeaponType type, long amount) throws IOException {
        if (amount <= 0L) {
            throw new IllegalArgumentException(services.messages().get("growth.mastery.invalid-amount"));
        }

        PlayerStats stats = services.playerStats().loadOrCreate(playerId);
        return new ProgressionResult(null, stats, List.of("\u6b66\u5668\u719f\u7df4\u5ea6\u6210\u9577\u306f\u73fe\u5728\u7121\u52b9\u3067\u3059\u3002"));
    }
}
