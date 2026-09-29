package com.eltena.core.domain.player;

public record PlayerAttributes(
    int strength,
    int dexterity,
    int intelligence,
    int vitality,
    int defense,
    double critRate,
    double critDamage
) {

    public PlayerAttributes {
        strength = Math.max(0, strength);
        dexterity = Math.max(0, dexterity);
        intelligence = Math.max(0, intelligence);
        vitality = Math.max(0, vitality);
        defense = Math.max(0, defense);
        critRate = Math.max(0.0D, Math.min(1.0D, critRate));
        critDamage = Math.max(1.0D, critDamage);
    }

    public static PlayerAttributes createDefault() {
        return new PlayerAttributes(10, 10, 10, 10, 10, 0.05D, 1.5D);
    }
}
