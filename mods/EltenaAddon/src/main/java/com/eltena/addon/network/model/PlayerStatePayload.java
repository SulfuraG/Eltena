package com.eltena.addon.network.model;

import com.eltena.addon.network.SyncPayload;

public record PlayerStatePayload(
    String playerName,
    String playerUuid,
    int level,
    long exp,
    long maxExp,
    String currentJobId,
    String currentJobName,
    String worldRankId,
    String worldRankName,
    long worldRankExp,
    String currentTitleId,
    String currentTitleName,
    int hp,
    int maxHp,
    int mp,
    int maxMp,
    int attack,
    int baseAttack,
    double weaponAttack,
    double attackPower,
    int defense,
    String weaponType,
    String weaponCategory,
    int skillPoint
) implements SyncPayload {
}
