package com.eltena.core.domain.player;

public record PlayerStatBonuses(
    double strength,
    double dexterity,
    double intelligence,
    double vitality,
    double defense,
    double critRate,
    double critDamage
) {

    public static PlayerStatBonuses none() {
        return new PlayerStatBonuses(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    public PlayerStatBonuses add(PlayerStatBonuses other) {
        if (other == null) {
            return this;
        }
        return new PlayerStatBonuses(
            strength + other.strength(),
            dexterity + other.dexterity(),
            intelligence + other.intelligence(),
            vitality + other.vitality(),
            defense + other.defense(),
            critRate + other.critRate(),
            critDamage + other.critDamage()
        );
    }

    public static PlayerStatBonuses single(String statId, double amount) {
        if (statId == null || statId.isBlank() || amount == 0.0D) {
            return none();
        }
        return switch (statId.trim().toLowerCase()) {
            case "strength" -> new PlayerStatBonuses(amount, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
            case "dexterity" -> new PlayerStatBonuses(0.0D, amount, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
            case "intelligence" -> new PlayerStatBonuses(0.0D, 0.0D, amount, 0.0D, 0.0D, 0.0D, 0.0D);
            case "vitality" -> new PlayerStatBonuses(0.0D, 0.0D, 0.0D, amount, 0.0D, 0.0D, 0.0D);
            case "defense" -> new PlayerStatBonuses(0.0D, 0.0D, 0.0D, 0.0D, amount, 0.0D, 0.0D);
            case "crit-rate", "crit_rate" -> new PlayerStatBonuses(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, amount, 0.0D);
            case "crit-damage", "crit_damage" -> new PlayerStatBonuses(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, amount);
            default -> none();
        };
    }
}
