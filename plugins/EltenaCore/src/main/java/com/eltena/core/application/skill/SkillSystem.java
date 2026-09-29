package com.eltena.core.application.skill;

import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.player.PlayerProfile;
import com.eltena.core.domain.player.PlayerStatBonuses;
import com.eltena.core.domain.skill.SkillDefinition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SkillSystem {
    private final ServiceRegistry services;
    private final SkillCatalog catalog;
    private final SkillLoader loader;

    public SkillSystem(ServiceRegistry services, SkillCatalog catalog, SkillLoader loader) {
        this.services = Objects.requireNonNull(services, "services");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    public int reload() {
        return loader.loadInto(catalog);
    }

    public List<SkillDefinition> listDefinitions() {
        return catalog.list();
    }

    public SkillDefinition requireDefinition(String skillId) {
        SkillDefinition definition = catalog.find(skillId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown skill: " + skillId);
        }
        return definition;
    }

    public int currentRank(PlayerProfile profile, String skillId) {
        return Math.max(0, profile.skillRanks().getOrDefault(skillId, 0));
    }

    public int availableSkillPoints(PlayerProfile profile) {
        return Math.max(0, profile.skillPoint());
    }

    public boolean meetsLevelRequirement(PlayerProfile profile, SkillDefinition definition) {
        if (profile == null || definition == null) {
            return false;
        }
        return profile.level() >= Math.max(0, definition.requiredLevel());
    }

    public PlayerStatBonuses resolveStatBonuses(PlayerProfile profile) {
        if (profile == null) {
            return PlayerStatBonuses.none();
        }
        PlayerStatBonuses bonuses = PlayerStatBonuses.none();
        for (SkillDefinition definition : catalog.list()) {
            int rank = currentRank(profile, definition.id());
            if (rank <= 0) {
                continue;
            }
            var effect = definition.effect("stats");
            if (effect.isEmpty()) {
                continue;
            }
            for (var entry : effect.entrySet()) {
                java.util.Map<String, Object> statConfig = asMap(entry.getValue());
                double value = resolveStatMagnitude(statConfig, rank);
                bonuses = bonuses.add(PlayerStatBonuses.single(entry.getKey(), value));
            }
        }
        return bonuses;
    }

    public AttackDamageAdjustment resolveAttackDamageAdjustment(PlayerProfile profile, double baseDamage) {
        if (profile == null) {
            return AttackDamageAdjustment.none();
        }
        double flatBonus = 0.0D;
        double percentBonus = 0.0D;
        for (SkillDefinition definition : catalog.list()) {
            int rank = currentRank(profile, definition.id());
            if (rank <= 0) {
                continue;
            }
            var effect = definition.effect("attack-damage");
            if (effect.isEmpty()) {
                continue;
            }
            String type = String.valueOf(effect.getOrDefault("type", "")).trim().toLowerCase();
            double magnitude = resolveEffectMagnitude(effect, rank);
            if (magnitude == 0.0D) {
                continue;
            }
            switch (type) {
                case "flat" -> flatBonus += magnitude;
                case "percent" -> percentBonus += magnitude;
                default -> {
                }
            }
        }
        return AttackDamageAdjustment.of(baseDamage, flatBonus, percentBonus);
    }

    public List<String> missingPrerequisites(PlayerProfile profile, SkillDefinition definition) {
        List<String> missing = new ArrayList<>();
        SkillDefinition.Requirements requirements = definition.requirements();
        if (requirements.all().stream().anyMatch(required -> currentRank(profile, required) <= 0)) {
            missing.addAll(
                requirements.all().stream()
                    .filter(required -> currentRank(profile, required) <= 0)
                    .toList()
            );
        }
        if (!requirements.any().isEmpty() && requirements.any().stream().noneMatch(required -> currentRank(profile, required) > 0)) {
            missing.add("any:" + String.join("|", requirements.any()));
        }
        for (var entry : requirements.ranks().entrySet()) {
            if (currentRank(profile, entry.getKey()) < Math.max(1, entry.getValue())) {
                missing.add(entry.getKey() + "@" + entry.getValue());
            }
        }
        return missing;
    }

    public SkillProgressResult learn(UUID playerId, String playerName, String skillId) throws IOException {
        SkillDefinition definition = requireDefinition(skillId);
        PlayerProfile profile = services.playerProfiles().loadOrCreate(playerId, playerName);
        SkillProgressResult result = applyLearn(profile, definition);
        services.playerProfiles().save(result.profile());
        return result;
    }

    private SkillProgressResult applyLearn(PlayerProfile profile, SkillDefinition definition) {
        int currentRank = currentRank(profile, definition.id());
        if (currentRank >= definition.maxRank()) {
            return denied(profile, definition, "max-rank");
        }
        if (!meetsLevelRequirement(profile, definition)) {
            return denied(profile, definition, "required-level");
        }
        if (availableSkillPoints(profile) < definition.requiredPoints()) {
            return denied(profile, definition, "insufficient-points");
        }
        List<String> missing = missingPrerequisites(profile, definition);
        if (!missing.isEmpty()) {
            return denied(profile, definition, "missing-prerequisites");
        }

        int nextRank = currentRank + 1;
        int remainingPoints = Math.max(0, availableSkillPoints(profile) - definition.requiredPoints());
        PlayerProfile updated = profile.withSkillPoint(remainingPoints).withSkillRank(definition.id(), nextRank);
        updated = services.abilitySystem().applyUnlocks(updated, definition.unlockAbilities());
        String messageKey = definition.maxRank() <= 1 ? "skill.learned" : "skill.rank-up";
        return new SkillProgressResult(
            updated,
            List.of(services.messages().get(
                messageKey,
                "skill", definition.displayName(),
                "rank", nextRank,
                "maxRank", definition.maxRank()
            )),
            true,
            true,
            "",
            definition.id(),
            nextRank,
            definition.maxRank()
        );
    }

    private SkillProgressResult denied(PlayerProfile profile, SkillDefinition definition, String reason) {
        return new SkillProgressResult(
            profile,
            List.of(),
            false,
            false,
            reason,
            definition.id(),
            currentRank(profile, definition.id()),
            definition.maxRank()
        );
    }

    private double resolveEffectMagnitude(java.util.Map<String, Object> effect, int rank) {
        double value = asDouble(effect.get("value"));
        double valuePerRank = asDouble(effect.get("value-per-rank"));
        return value + valuePerRank * Math.max(0, rank);
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0.0D;
    }

    private java.util.Map<String, Object> asMap(Object value) {
        if (!(value instanceof java.util.Map<?, ?> map)) {
            return java.util.Map.of();
        }
        java.util.LinkedHashMap<String, Object> normalized = new java.util.LinkedHashMap<>();
        for (var entry : map.entrySet()) {
            if (entry.getKey() != null) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return normalized;
    }

    private double resolveStatMagnitude(java.util.Map<String, Object> effect, int rank) {
        return asDouble(effect.get("value"))
            + asDouble(effect.get("flat"))
            + asDouble(effect.get("value-per-rank")) * Math.max(0, rank)
            + asDouble(effect.get("flat-per-rank")) * Math.max(0, rank);
    }

    public record AttackDamageAdjustment(double flatBonus, double percentBonus, double multiplier) {
        public static AttackDamageAdjustment none() {
            return new AttackDamageAdjustment(0.0D, 0.0D, 1.0D);
        }

        public static AttackDamageAdjustment of(double baseDamage, double flatBonus, double percentBonus) {
            double percentMultiplier = Math.max(0.0D, 1.0D + (percentBonus / 100.0D));
            if (baseDamage <= 0.0D) {
                return new AttackDamageAdjustment(flatBonus, percentBonus, percentMultiplier);
            }
            double flatMultiplier = Math.max(0.0D, (baseDamage + flatBonus) / baseDamage);
            return new AttackDamageAdjustment(flatBonus, percentBonus, flatMultiplier * percentMultiplier);
        }
    }
}
