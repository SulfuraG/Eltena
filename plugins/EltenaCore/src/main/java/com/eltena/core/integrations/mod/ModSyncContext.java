package com.eltena.core.integrations.mod;

import com.eltena.core.application.equipment.MmoItemsEquipmentBonuses;
import com.eltena.core.domain.player.FinalPlayerStats;
import com.eltena.core.domain.player.PlayerProfile;
import com.eltena.core.domain.stats.PlayerStats;

public record ModSyncContext(
    PlayerProfile profile,
    PlayerStats stats,
    FinalPlayerStats finalStats,
    MmoItemsEquipmentBonuses equipmentBonuses
) {
}
