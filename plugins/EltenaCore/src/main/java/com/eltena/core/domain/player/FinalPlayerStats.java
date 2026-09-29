package com.eltena.core.domain.player;

public record FinalPlayerStats(
    double strength,
    double dexterity,
    double intelligence,
    double vitality,
    double defense,
    double critRate,
    double critDamage,
    double physicalDamageMultiplier,
    int hpBonus,
    double dexCritBonus,
    double accuracy,
    double masteryGainMultiplier,
    int maxMpBonus,
    double cooldownReduction
) {

    public FinalPlayerStats {
        strength = Math.max(0.0D, strength);
        dexterity = Math.max(0.0D, dexterity);
        intelligence = Math.max(0.0D, intelligence);
        vitality = Math.max(0.0D, vitality);
        defense = Math.max(0.0D, defense);
        critRate = Math.max(0.0D, Math.min(1.0D, critRate));
        critDamage = Math.max(1.0D, critDamage);
        physicalDamageMultiplier = Math.max(0.0D, physicalDamageMultiplier);
        hpBonus = Math.max(0, hpBonus);
        dexCritBonus = Math.max(0.0D, dexCritBonus);
        accuracy = Math.max(0.0D, accuracy);
        masteryGainMultiplier = Math.max(1.0D, masteryGainMultiplier);
        maxMpBonus = Math.max(0, maxMpBonus);
        cooldownReduction = Math.max(0.0D, Math.min(1.0D, cooldownReduction));
    }

    public FinalPlayerStats apply(PlayerStatBonuses bonuses) {
        if (bonuses == null) {
            return this;
        }
        return new FinalPlayerStats(
            strength + bonuses.strength(),
            dexterity + bonuses.dexterity(),
            intelligence + bonuses.intelligence(),
            vitality + bonuses.vitality(),
            defense + bonuses.defense(),
            critRate + bonuses.critRate(),
            critDamage + bonuses.critDamage(),
            physicalDamageMultiplier,
            hpBonus,
            dexCritBonus,
            accuracy,
            masteryGainMultiplier,
            maxMpBonus,
            cooldownReduction
        );
    }

    public FinalPlayerStats withDerivedBonuses(
        double physicalDamageMultiplier,
        int hpBonus,
        double dexCritBonus,
        double accuracy,
        double masteryGainMultiplier,
        int maxMpBonus,
        double cooldownReduction
    ) {
        return new FinalPlayerStats(
            strength,
            dexterity,
            intelligence,
            vitality,
            defense,
            critRate + dexCritBonus,
            critDamage,
            physicalDamageMultiplier,
            hpBonus,
            dexCritBonus,
            accuracy,
            masteryGainMultiplier,
            maxMpBonus,
            cooldownReduction
        );
    }
}
