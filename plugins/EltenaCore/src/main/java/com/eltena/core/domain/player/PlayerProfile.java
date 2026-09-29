package com.eltena.core.domain.player;

import com.eltena.core.application.ability.AbilityCatalog;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record PlayerProfile(
    UUID playerId,
    String playerName,
    int level,
    long experience,
    String currentJobId,
    String worldRankId,
    long worldRankExperience,
    String activeTitleId,
    int skillPoint,
    Set<String> unlockedJobs,
    Set<String> unlockedTitles,
    Set<String> discoveredExplorations,
    int discoveryCount,
    Map<String, Integer> skillRanks,
    Set<String> unlockedAbilities,
    Map<String, String> equippedAbilities,
    BasePlayerStats stats,
    Instant createdAt,
    Instant updatedAt
) {

    public PlayerProfile {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(currentJobId, "currentJobId");
        Objects.requireNonNull(worldRankId, "worldRankId");
        Objects.requireNonNull(activeTitleId, "activeTitleId");
        unlockedJobs = Set.copyOf(Objects.requireNonNull(unlockedJobs, "unlockedJobs"));
        unlockedTitles = Set.copyOf(Objects.requireNonNull(unlockedTitles, "unlockedTitles"));
        discoveredExplorations = Set.copyOf(Objects.requireNonNull(discoveredExplorations, "discoveredExplorations"));
        skillRanks = Map.copyOf(Objects.requireNonNull(skillRanks, "skillRanks"));
        unlockedAbilities = Set.copyOf(normalizeUnlockedAbilities(Objects.requireNonNull(unlockedAbilities, "unlockedAbilities")));
        equippedAbilities = Map.copyOf(normalizeEquippedAbilities(Objects.requireNonNull(equippedAbilities, "equippedAbilities")));
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static PlayerProfile createDefault(UUID playerId, String playerName) {
        Instant now = Instant.now();
        LinkedHashSet<String> jobs = new LinkedHashSet<>();
        jobs.add("novice");
        LinkedHashSet<String> titles = new LinkedHashSet<>();
        titles.add("none");
        LinkedHashMap<String, Integer> initialSkillRanks = new LinkedHashMap<>();
        initialSkillRanks.put("basic_slash", 1);
        initialSkillRanks.put("quick_step", 1);
        LinkedHashMap<String, String> initialAbilitySlots = new LinkedHashMap<>();
        initialAbilitySlots.put("slot-1", "");
        initialAbilitySlots.put("slot-2", "");
        initialAbilitySlots.put("slot-3", "");
        initialAbilitySlots.put("slot-4", "");
        return new PlayerProfile(
            playerId,
            playerName,
            1,
            0L,
            "novice",
            "unranked",
            0L,
            "none",
            12,
            jobs,
            titles,
            Set.of(),
            0,
            initialSkillRanks,
            Set.of(),
            initialAbilitySlots,
            BasePlayerStats.createDefault(),
            now,
            now
        );
    }

    public PlayerProfile withPlayerName(String newName) {
        return copy(
            newName,
            level,
            experience,
            currentJobId,
            worldRankId,
            worldRankExperience,
            activeTitleId,
            skillPoint,
            unlockedJobs,
            unlockedTitles,
            discoveredExplorations,
            discoveryCount,
            skillRanks,
            unlockedAbilities,
            equippedAbilities,
            stats
        );
    }

    public PlayerProfile touch() {
        return copy(
            playerName,
            level,
            experience,
            currentJobId,
            worldRankId,
            worldRankExperience,
            activeTitleId,
            skillPoint,
            unlockedJobs,
            unlockedTitles,
            discoveredExplorations,
            discoveryCount,
            skillRanks,
            unlockedAbilities,
            equippedAbilities,
            stats
        );
    }

    public PlayerProfile withProgression(int newLevel, long newExperience) {
        return copy(
            playerName,
            newLevel,
            newExperience,
            currentJobId,
            worldRankId,
            worldRankExperience,
            activeTitleId,
            skillPoint,
            unlockedJobs,
            unlockedTitles,
            discoveredExplorations,
            discoveryCount,
            skillRanks,
            unlockedAbilities,
            equippedAbilities,
            stats
        );
    }

    public PlayerProfile withCurrentJob(String newJobId) {
        return copy(
            playerName,
            level,
            experience,
            newJobId,
            worldRankId,
            worldRankExperience,
            activeTitleId,
            skillPoint,
            unlockedJobs,
            unlockedTitles,
            discoveredExplorations,
            discoveryCount,
            skillRanks,
            unlockedAbilities,
            equippedAbilities,
            stats
        );
    }

    public PlayerProfile withWorldRank(String newWorldRankId, long newWorldRankExperience) {
        return copy(
            playerName,
            level,
            experience,
            currentJobId,
            newWorldRankId,
            newWorldRankExperience,
            activeTitleId,
            skillPoint,
            unlockedJobs,
            unlockedTitles,
            discoveredExplorations,
            discoveryCount,
            skillRanks,
            unlockedAbilities,
            equippedAbilities,
            stats
        );
    }

    public PlayerProfile withActiveTitle(String newTitleId) {
        return copy(
            playerName,
            level,
            experience,
            currentJobId,
            worldRankId,
            worldRankExperience,
            newTitleId,
            skillPoint,
            unlockedJobs,
            unlockedTitles,
            discoveredExplorations,
            discoveryCount,
            skillRanks,
            unlockedAbilities,
            equippedAbilities,
            stats
        );
    }

    public PlayerProfile withSkillPoint(int newSkillPoint) {
        return copy(
            playerName,
            level,
            experience,
            currentJobId,
            worldRankId,
            worldRankExperience,
            activeTitleId,
            newSkillPoint,
            unlockedJobs,
            unlockedTitles,
            discoveredExplorations,
            discoveryCount,
            skillRanks,
            unlockedAbilities,
            equippedAbilities,
            stats
        );
    }

    public PlayerProfile withUnlockedJob(String jobId) {
        LinkedHashSet<String> updated = new LinkedHashSet<>(unlockedJobs);
        updated.add(jobId);
        return copy(
            playerName,
            level,
            experience,
            currentJobId,
            worldRankId,
            worldRankExperience,
            activeTitleId,
            skillPoint,
            updated,
            unlockedTitles,
            discoveredExplorations,
            discoveryCount,
            skillRanks,
            unlockedAbilities,
            equippedAbilities,
            stats
        );
    }

    public PlayerProfile withUnlockedTitle(String titleId) {
        LinkedHashSet<String> updated = new LinkedHashSet<>(unlockedTitles);
        updated.add(titleId);
        return copy(
            playerName,
            level,
            experience,
            currentJobId,
            worldRankId,
            worldRankExperience,
            activeTitleId,
            skillPoint,
            unlockedJobs,
            updated,
            discoveredExplorations,
            discoveryCount,
            skillRanks,
            unlockedAbilities,
            equippedAbilities,
            stats
        );
    }

    public PlayerProfile withDiscoveredExploration(String discoveryId) {
        LinkedHashSet<String> updated = new LinkedHashSet<>(discoveredExplorations);
        updated.add(discoveryId);
        return copy(
            playerName,
            level,
            experience,
            currentJobId,
            worldRankId,
            worldRankExperience,
            activeTitleId,
            skillPoint,
            unlockedJobs,
            unlockedTitles,
            updated,
            updated.size(),
            skillRanks,
            unlockedAbilities,
            equippedAbilities,
            stats
        );
    }

    public PlayerProfile withSkillRanks(Map<String, Integer> newSkillRanks) {
        return copy(
            playerName,
            level,
            experience,
            currentJobId,
            worldRankId,
            worldRankExperience,
            activeTitleId,
            skillPoint,
            unlockedJobs,
            unlockedTitles,
            discoveredExplorations,
            discoveryCount,
            new LinkedHashMap<>(newSkillRanks),
            unlockedAbilities,
            equippedAbilities,
            stats
        );
    }

    public PlayerProfile withSkillRank(String skillId, int rank) {
        LinkedHashMap<String, Integer> updated = new LinkedHashMap<>(skillRanks);
        updated.put(skillId, Math.max(0, rank));
        return withSkillRanks(updated);
    }

    public PlayerProfile withUnlockedAbilities(Set<String> abilities) {
        return copy(
            playerName,
            level,
            experience,
            currentJobId,
            worldRankId,
            worldRankExperience,
            activeTitleId,
            skillPoint,
            unlockedJobs,
            unlockedTitles,
            discoveredExplorations,
            discoveryCount,
            skillRanks,
            new LinkedHashSet<>(abilities),
            equippedAbilities,
            stats
        );
    }

    public PlayerProfile withAddedUnlockedAbilities(Set<String> abilities) {
        LinkedHashSet<String> updated = new LinkedHashSet<>(unlockedAbilities);
        updated.addAll(abilities);
        return withUnlockedAbilities(updated);
    }

    public PlayerProfile withEquippedAbilities(Map<String, String> abilitiesBySlot) {
        return copy(
            playerName,
            level,
            experience,
            currentJobId,
            worldRankId,
            worldRankExperience,
            activeTitleId,
            skillPoint,
            unlockedJobs,
            unlockedTitles,
            discoveredExplorations,
            discoveryCount,
            skillRanks,
            unlockedAbilities,
            new LinkedHashMap<>(abilitiesBySlot),
            stats
        );
    }

    public List<String> equippedAbilityIds() {
        return equippedAbilities.values().stream()
            .filter(Objects::nonNull)
            .map(AbilityCatalog::normalizeId)
            .filter(Objects::nonNull)
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();
    }

    public PlayerProfile withStats(BasePlayerStats newStats) {
        return copy(
            playerName,
            level,
            experience,
            currentJobId,
            worldRankId,
            worldRankExperience,
            activeTitleId,
            skillPoint,
            unlockedJobs,
            unlockedTitles,
            discoveredExplorations,
            discoveryCount,
            skillRanks,
            unlockedAbilities,
            equippedAbilities,
            newStats
        );
    }

    private static Set<String> normalizeUnlockedAbilities(Set<String> abilities) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String abilityId : abilities) {
            String normalizedId = AbilityCatalog.normalizeId(abilityId);
            if (normalizedId != null) {
                normalized.add(normalizedId);
            }
        }
        return normalized;
    }

    private static Map<String, String> normalizeEquippedAbilities(Map<String, String> abilitiesBySlot) {
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : abilitiesBySlot.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            String normalizedAbilityId = AbilityCatalog.normalizeId(entry.getValue());
            normalized.put(entry.getKey(), normalizedAbilityId == null ? "" : normalizedAbilityId);
        }
        return normalized;
    }

    private PlayerProfile copy(
        String nextPlayerName,
        int nextLevel,
        long nextExperience,
        String nextCurrentJobId,
        String nextWorldRankId,
        long nextWorldRankExperience,
        String nextActiveTitleId,
        int nextSkillPoint,
        Set<String> nextUnlockedJobs,
        Set<String> nextUnlockedTitles,
        Set<String> nextDiscoveredExplorations,
        int nextDiscoveryCount,
        Map<String, Integer> nextSkillRanks,
        Set<String> nextUnlockedAbilities,
        Map<String, String> nextEquippedAbilities,
        BasePlayerStats nextStats
    ) {
        return new PlayerProfile(
            playerId,
            nextPlayerName,
            nextLevel,
            nextExperience,
            nextCurrentJobId,
            nextWorldRankId,
            nextWorldRankExperience,
            nextActiveTitleId,
            nextSkillPoint,
            nextUnlockedJobs,
            nextUnlockedTitles,
            nextDiscoveredExplorations,
            nextDiscoveryCount,
            nextSkillRanks,
            nextUnlockedAbilities,
            nextEquippedAbilities,
            nextStats,
            createdAt,
            Instant.now()
        );
    }
}
