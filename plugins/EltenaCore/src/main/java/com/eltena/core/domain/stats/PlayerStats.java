package com.eltena.core.domain.stats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record PlayerStats(
    UUID playerId,
    double hpExact,
    int mp,
    double maxMpExact,
    boolean manaMigrated,
    int attack,
    int defense,
    Map<WeaponType, WeaponMasteryProgress> weaponMastery,
    Map<String, Integer> jobAffinity,
    Map<String, Integer> subGrowth,
    Map<GrowthType, StatGrowthProgress> statGrowth
) {

    public PlayerStats {
        Objects.requireNonNull(playerId, "playerId");
        hpExact = Math.max(1.0D, hpExact);
        maxMpExact = Math.max(1.0D, maxMpExact);
        mp = Math.max(0, mp);
        attack = Math.max(0, attack);
        defense = Math.max(0, defense);
        weaponMastery = Map.copyOf(Objects.requireNonNull(weaponMastery, "weaponMastery"));
        jobAffinity = Map.copyOf(Objects.requireNonNull(jobAffinity, "jobAffinity"));
        subGrowth = Map.copyOf(Objects.requireNonNull(subGrowth, "subGrowth"));
        statGrowth = Map.copyOf(Objects.requireNonNull(statGrowth, "statGrowth"));
    }

    public static PlayerStats createDefault(UUID playerId) {
        EnumMap<WeaponType, WeaponMasteryProgress> weaponMastery = new EnumMap<>(WeaponType.class);
        for (WeaponType type : WeaponType.values()) {
            weaponMastery.put(type, WeaponMasteryProgress.initial());
        }

        EnumMap<GrowthType, StatGrowthProgress> growth = new EnumMap<>(GrowthType.class);
        for (GrowthType type : GrowthType.values()) {
            growth.put(type, StatGrowthProgress.initial());
        }

        return new PlayerStats(
            playerId,
            20.0D,
            10,
            20.0D,
            false,
            5,
            5,
            weaponMastery,
            Map.of("novice", 0),
            Map.of("vitality", 0, "focus", 0, "agility", 0),
            growth
        );
    }

    public int hp() {
        return roundedValue(hpExact);
    }

    public int maxMp() {
        return roundedValue(maxMpExact);
    }

    public PlayerStats withCoreStats(double newHpExact, int newMp, double newMaxMpExact, int newAttack, int newDefense) {
        return new PlayerStats(
            playerId,
            newHpExact,
            newMp,
            newMaxMpExact,
            manaMigrated,
            newAttack,
            newDefense,
            weaponMastery,
            jobAffinity,
            subGrowth,
            statGrowth
        );
    }

    public PlayerStats withMana(int newMp, double newMaxMpExact, boolean migrated) {
        return new PlayerStats(
            playerId,
            hpExact,
            newMp,
            newMaxMpExact,
            migrated,
            attack,
            defense,
            weaponMastery,
            jobAffinity,
            subGrowth,
            statGrowth
        );
    }

    public PlayerStats withWeaponMastery(Map<WeaponType, WeaponMasteryProgress> newWeaponMastery) {
        return new PlayerStats(playerId, hpExact, mp, maxMpExact, manaMigrated, attack, defense, newWeaponMastery, jobAffinity, subGrowth, statGrowth);
    }

    public PlayerStats withSubGrowth(Map<String, Integer> newSubGrowth) {
        return new PlayerStats(playerId, hpExact, mp, maxMpExact, manaMigrated, attack, defense, weaponMastery, jobAffinity, newSubGrowth, statGrowth);
    }

    public PlayerStats withStatGrowth(Map<GrowthType, StatGrowthProgress> newStatGrowth) {
        return new PlayerStats(playerId, hpExact, mp, maxMpExact, manaMigrated, attack, defense, weaponMastery, jobAffinity, subGrowth, newStatGrowth);
    }

    public static int roundedValue(double value) {
        return Math.max(1, (int) Math.round(value));
    }

    public static double roundExact(double value, int decimals) {
        int scale = Math.max(0, decimals);
        return BigDecimal.valueOf(value)
            .setScale(scale, RoundingMode.HALF_UP)
            .doubleValue();
    }
}
