package com.eltena.core.application.progression;

import com.eltena.core.application.growth.GrowthSettings;
import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.player.BasePlayerStats;
import com.eltena.core.domain.player.FinalPlayerStats;
import com.eltena.core.domain.player.PlayerProfile;
import com.eltena.core.domain.skill.SkillDefinition;
import com.eltena.core.domain.stats.PlayerStats;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class LevelSystem {

    private static final String[] MAX_HEALTH_ATTRIBUTE_NAMES = {
        "MAX_HEALTH",
        "GENERIC_MAX_HEALTH"
    };

    private static final DecimalFormat REWARD_FORMAT = new DecimalFormat(
        "0.##",
        DecimalFormatSymbols.getInstance(Locale.ROOT)
    );

    private final ServiceRegistry services;

    public LevelSystem(ServiceRegistry services) {
        this.services = services;
    }

    public ProgressionResult addExperience(UUID playerId, String playerName, long amount) throws IOException {
        if (amount <= 0L) {
            throw new IllegalArgumentException(services.messages().get("growth.exp.invalid-amount"));
        }

        GrowthSettings settings = services.growthSettings();
        PlayerProfile profile = services.playerProfiles().loadOrCreate(playerId, playerName);
        PlayerStats stats = services.playerStats().loadOrCreate(playerId);
        List<String> messages = new ArrayList<>();

        int previousLevel = Math.max(1, profile.level());
        int levelCap = settings.levelCap();
        int currentLevel = Math.min(previousLevel, levelCap);
        long remainingExp = currentLevel >= levelCap ? 0L : profile.experience() + amount;
        PlayerStats updatedStats = stats;
        BasePlayerStats updatedAttributes = profile.stats();
        GrowthSettings.LevelUpReward aggregatedReward = GrowthSettings.LevelUpReward.none();

        while (currentLevel < levelCap && remainingExp >= requiredExpFor(currentLevel)) {
            remainingExp -= requiredExpFor(currentLevel);
            currentLevel++;

            GrowthSettings.LevelUpReward reward = settings.levelUpRewardFor(updatedStats.hpExact(), updatedStats.maxMpExact());
            aggregatedReward = aggregatedReward.add(reward);
            updatedStats = applyLevelReward(updatedStats, reward, settings.restoreMpOnLevelUp());
            updatedAttributes = applyAttributeReward(updatedAttributes, reward);

            services.plugin().getLogger().info(
                "[EltenaCore] Level up: " + playerName + " " + (currentLevel - 1) + " -> " + currentLevel
            );
        }

        if (currentLevel >= levelCap) {
            remainingExp = 0L;
        }

        PlayerProfile updatedProfile = profile.withStats(updatedAttributes).withProgression(currentLevel, remainingExp);
        updatedProfile = applySkillAvailabilityProgression(updatedProfile, previousLevel, currentLevel, messages);
        updatedProfile = applyAbilityUnlockProgression(updatedProfile, previousLevel, currentLevel, messages);
        Set<String> newlyUnlockedAbilities = new LinkedHashSet<>(updatedProfile.unlockedAbilities());
        newlyUnlockedAbilities.removeAll(profile.unlockedAbilities());
        services.playerProfiles().save(updatedProfile);
        services.playerStats().save(updatedStats);

        messages.add(services.messages().get("growth.exp.added", "amount", amount));
        if (currentLevel > previousLevel) {
            messages.add(services.messages().get("growth.level.up", "level", currentLevel));
            if (hasAppliedReward(aggregatedReward)) {
                messages.add(services.messages().get("growth.level.reward-summary", "summary", rewardSummary(aggregatedReward)));
            }
            notifyLevelUp(services.plugin().getServer().getPlayer(playerId), currentLevel, aggregatedReward);
        }

        Player onlinePlayer = services.plugin().getServer().getPlayer(playerId);
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            notifyAbilityUnlocks(onlinePlayer, newlyUnlockedAbilities);
            notifySkillAvailability(onlinePlayer, updatedProfile, previousLevel, currentLevel);
            services.playerDerivedStatsService().apply(onlinePlayer);
            if (currentLevel > previousLevel) {
                restoreResourcesOnLevelUp(onlinePlayer, updatedProfile);
                playLevelUpSound(onlinePlayer);
            }
            services.modSyncPluginMessenger().sendPlayerState(onlinePlayer);
            services.modSyncPluginMessenger().sendAbilityState(onlinePlayer);
            services.modSyncPluginMessenger().sendSkillTree(onlinePlayer);
        }

        return new ProgressionResult(updatedProfile, updatedStats, messages, amount, previousLevel, currentLevel);
    }

    public long requiredExpFor(int level) {
        return services.growthSettings().requiredExpForLevel(level);
    }

    private PlayerStats applyLevelReward(PlayerStats stats, GrowthSettings.LevelUpReward reward, boolean restoreMpOnLevelUp) {
        double nextHpExact = stats.hpExact() + reward.hp();
        double nextMaxMpExact = stats.maxMpExact() + reward.maxMp();
        int nextRoundedMaxMp = PlayerStats.roundedValue(nextMaxMpExact);
        int nextMp = restoreMpOnLevelUp
            ? nextRoundedMaxMp
            : Math.min(stats.mp(), nextRoundedMaxMp);
        return stats.withCoreStats(nextHpExact, nextMp, nextMaxMpExact, stats.attack(), stats.defense());
    }

    private BasePlayerStats applyAttributeReward(BasePlayerStats stats, GrowthSettings.LevelUpReward reward) {
        return stats;
    }

    private void restoreResourcesOnLevelUp(Player player, PlayerProfile profile) throws IOException {
        restoreFullHealth(player);
        FinalPlayerStats finalStats = services.finalPlayerStatsCalculator().calculate(profile);
        services.playerManaService().restoreFull(player.getUniqueId(), finalStats);
    }

    private void restoreFullHealth(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        Attribute attributeType = resolveMaxHealthAttribute();
        if (attributeType == null) {
            return;
        }
        AttributeInstance attribute = player.getAttribute(attributeType);
        if (attribute == null) {
            return;
        }
        double liveMaxHealth = Math.max(1.0D, attribute.getValue());
        player.setHealth(liveMaxHealth);
    }

    private void playLevelUpSound(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        GrowthSettings settings = services.growthSettings();
        if (!settings.levelUpSoundEnabled()) {
            return;
        }
        Sound sound = parseLevelUpSound(settings.levelUpSoundName());
        if (sound == null) {
            return;
        }
        player.playSound(player.getLocation(), sound, settings.levelUpSoundVolume(), settings.levelUpSoundPitch());
    }

    private Sound parseLevelUpSound(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        try {
            return Sound.valueOf(rawName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            services.plugin().getLogger().warning("[EltenaCore] Invalid level-up sound configured: " + rawName);
            return null;
        }
    }

    private Attribute resolveMaxHealthAttribute() {
        for (String candidate : MAX_HEALTH_ATTRIBUTE_NAMES) {
            try {
                return Attribute.valueOf(candidate);
            } catch (IllegalArgumentException ignored) {
                // Try the next runtime-compatible attribute name.
            }
        }
        return null;
    }

    private void notifyLevelUp(Player player, int level, GrowthSettings.LevelUpReward reward) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.sendMessage(color(services.messages().get("growth.level.up", "level", level)));
        if (hasAppliedReward(reward)) {
            player.sendMessage(color(services.messages().get("growth.level.reward-summary", "summary", rewardSummary(reward))));
        }
    }

    private String rewardSummary(GrowthSettings.LevelUpReward reward) {
        List<String> entries = new ArrayList<>();
        append(entries, "growth.level.reward.hp", reward.hp());
        append(entries, "growth.level.reward.max-mp", reward.maxMp());
        return String.join(" / ", entries);
    }

    private boolean hasAppliedReward(GrowthSettings.LevelUpReward reward) {
        return reward != null && (reward.hp() > 0 || reward.maxMp() > 0);
    }

    private void append(List<String> entries, String key, double value) {
        if (value <= 0.0D) {
            return;
        }
        entries.add(services.messages().get(key, "value", REWARD_FORMAT.format(value)));
    }

    private PlayerProfile applySkillAvailabilityProgression(
        PlayerProfile profile,
        int previousLevel,
        int currentLevel,
        List<String> messages
    ) {
        if (currentLevel <= previousLevel) {
            return profile;
        }
        for (SkillDefinition definition : services.skillSystem().listDefinitions()) {
            int requiredLevel = Math.max(0, definition.requiredLevel());
            if (requiredLevel <= 0 || previousLevel >= requiredLevel || currentLevel < requiredLevel) {
                continue;
            }
            if (services.skillSystem().currentRank(profile, definition.id()) > 0) {
                continue;
            }
            messages.add(services.messages().get("growth.skill.available", "skill", definition.displayName(), "level", requiredLevel));
            messages.add(services.messages().get("growth.skill.learn-in-tree"));
        }
        return profile;
    }

    private PlayerProfile applyAbilityUnlockProgression(
        PlayerProfile profile,
        int previousLevel,
        int currentLevel,
        List<String> messages
    ) {
        if (currentLevel <= previousLevel) {
            return profile;
        }
        PlayerProfile updated = profile;
        for (var entry : services.growthSettings().abilityUnlockByLevel().entrySet()) {
            int unlockLevel = Math.max(1, entry.getValue());
            if (currentLevel < unlockLevel || previousLevel >= unlockLevel) {
                continue;
            }
            PlayerProfile unlocked = services.abilitySystem().unlockAbility(updated, entry.getKey());
            if (unlocked == updated) {
                continue;
            }
            updated = unlocked;
            String abilityName = abilityDisplayName(entry.getKey());
            messages.add(services.messages().get("growth.ability.unlocked", "ability", abilityName));
        }
        return updated;
    }

    private void notifyAbilityUnlocks(Player player, Set<String> abilityIds) {
        if (player == null || !player.isOnline() || abilityIds == null || abilityIds.isEmpty()) {
            return;
        }
        for (String abilityId : abilityIds) {
            player.sendMessage(color(services.messages().get("growth.ability.unlocked", "ability", abilityDisplayName(abilityId))));
        }
    }

    private void notifySkillAvailability(
        Player player,
        PlayerProfile profile,
        int previousLevel,
        int currentLevel
    ) {
        if (player == null || !player.isOnline() || profile == null || currentLevel <= previousLevel) {
            return;
        }
        for (SkillDefinition definition : services.skillSystem().listDefinitions()) {
            int requiredLevel = Math.max(0, definition.requiredLevel());
            if (requiredLevel <= 0 || previousLevel >= requiredLevel || currentLevel < requiredLevel) {
                continue;
            }
            if (services.skillSystem().currentRank(profile, definition.id()) > 0) {
                continue;
            }
            services.modSyncPluginMessenger().sendNotification(
                player,
                services.messages().get("growth.skill.available", "skill", definition.displayName(), "level", requiredLevel),
                "info"
            );
            services.modSyncPluginMessenger().sendNotification(
                player,
                services.messages().get("growth.skill.learn-in-tree"),
                "info"
            );
        }
    }

    private String abilityDisplayName(String abilityId) {
        var definition = services.abilitySystem().findDefinition(abilityId);
        return definition == null ? abilityId : definition.displayName();
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
