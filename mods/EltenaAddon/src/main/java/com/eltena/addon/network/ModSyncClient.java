package com.eltena.addon.network;

import com.eltena.addon.network.model.AbilityRadialSlotPayload;
import com.eltena.addon.network.model.AbilityStatePayload;
import com.eltena.addon.network.model.AbilitySummaryPayload;
import com.eltena.addon.network.model.ModSyncEnvelope;
import com.eltena.addon.network.model.PlayerStatePayload;
import com.eltena.addon.network.model.SkillTreePayload;
import java.util.EnumMap;
import java.util.Map;

/**
 * Holds the newest synced payloads on the addon side.
 * The addon only reads this cache for presentation and never becomes authoritative.
 */
public final class ModSyncClient {
    private static final String DEBUG_PROPERTY = "eltena.syncDebug";
    private static final String LEGACY_DEBUG_PROPERTY = "eltena.debug";
    private static final Map<AddonSyncChannel, SyncPayload> LATEST_PAYLOADS = new EnumMap<>(AddonSyncChannel.class);

    private static String lastSkillTreeSyncAt = "";
    private static int lastSkillTreeNodeCount;
    private static String lastSkillTreeSyncSource = "";
    private static String lastSkillTreeSyncError = "";

    private static String lastPlayerStateSyncAt = "";
    private static String lastPlayerStateSyncSource = "";
    private static String lastPlayerStateSyncError = "";

    private static String lastAbilityStateSyncAt = "";
    private static String lastAbilityStateSyncSource = "";
    private static String lastAbilityStateSyncError = "";
    private static int lastAbilityStateUnlockedCount;
    private static long lastAbilityStateReceivedAtMillis;

    private ModSyncClient() {
    }

    public static void acceptEnvelope(ModSyncEnvelope<? extends SyncPayload> envelope) {
        if (envelope == null || envelope.payload() == null) {
            return;
        }
        boolean hadLivePlayerState = hasLivePlayerState();
        LATEST_PAYLOADS.put(envelope.channel(), envelope.payload());

        if (envelope.channel() == AddonSyncChannel.PLAYER_STATE && envelope.payload() instanceof PlayerStatePayload payload) {
            lastPlayerStateSyncAt = envelope.generatedAt() == null ? "" : envelope.generatedAt();
            lastPlayerStateSyncSource = "player_state";
            lastPlayerStateSyncError = "";
            if (!hadLivePlayerState) {
                System.out.println("[EltenaAddon] Live sync became available via player_state.");
            }
            if (isDebugEnabled()) {
                System.out.println(
                    "[EltenaAddon] Cached player_state"
                        + " level=" + payload.level()
                        + " exp=" + payload.exp() + "/" + payload.maxExp()
                        + " hp=" + payload.hp() + "/" + payload.maxHp()
                        + " mp=" + payload.mp() + "/" + payload.maxMp()
                        + " attack=" + payload.attack()
                        + " weaponAttack=" + payload.weaponAttack()
                        + " attackPower=" + payload.attackPower()
                        + " defense=" + payload.defense()
                        + " job=" + payload.currentJobName()
                );
            }
            return;
        }

        if (envelope.channel() == AddonSyncChannel.SKILL_TREE && envelope.payload() instanceof SkillTreePayload payload) {
            lastSkillTreeSyncAt = envelope.generatedAt() == null ? "" : envelope.generatedAt();
            lastSkillTreeNodeCount = payload.nodes().size();
            lastSkillTreeSyncSource = "skill_tree";
            lastSkillTreeSyncError = "";
            if (isDebugEnabled()) {
                System.out.println(
                    "[EltenaAddon] Cached skill_tree"
                        + " nodes=" + payload.nodes().size()
                        + " job=" + payload.currentJobName()
                        + " skillPoint=" + payload.skillPoint()
                );
            }
            return;
        }

        if (envelope.channel() == AddonSyncChannel.ABILITY_STATE && envelope.payload() instanceof AbilityStatePayload payload) {
            lastAbilityStateSyncAt = envelope.generatedAt() == null ? "" : envelope.generatedAt();
            lastAbilityStateSyncSource = "ability_state";
            lastAbilityStateSyncError = "";
            lastAbilityStateUnlockedCount = payload.unlockedAbilities().size();
            lastAbilityStateReceivedAtMillis = System.currentTimeMillis();
            if (isDebugEnabled()) {
                System.out.println(
                    "[EltenaAddon] Cached ability_state"
                        + " unlocked=" + payload.unlockedAbilities().size()
                        + " radial=" + payload.radialSlots().size()
                );
            }
        }
    }

    public static SkillTreePayload getSkillTreeState() {
        SyncPayload payload = LATEST_PAYLOADS.get(AddonSyncChannel.SKILL_TREE);
        return payload instanceof SkillTreePayload skillTreePayload ? skillTreePayload : null;
    }

    public static AbilityStatePayload getAbilityState() {
        SyncPayload payload = LATEST_PAYLOADS.get(AddonSyncChannel.ABILITY_STATE);
        return payload instanceof AbilityStatePayload abilityStatePayload ? abilityStatePayload : null;
    }

    public static PlayerStatePayload getPlayerState() {
        SyncPayload payload = LATEST_PAYLOADS.get(AddonSyncChannel.PLAYER_STATE);
        return payload instanceof PlayerStatePayload playerStatePayload ? playerStatePayload : null;
    }

    public static boolean hasLivePlayerState() {
        return getPlayerState() != null;
    }

    public static String lastSkillTreeSyncAt() {
        return lastSkillTreeSyncAt;
    }

    public static int lastSkillTreeNodeCount() {
        return lastSkillTreeNodeCount;
    }

    public static String lastSkillTreeSyncSource() {
        return lastSkillTreeSyncSource;
    }

    public static String lastSkillTreeSyncError() {
        return lastSkillTreeSyncError;
    }

    public static void markSkillTreeSyncError(String error) {
        lastSkillTreeSyncError = error == null ? "" : error;
    }

    public static String lastPlayerStateSyncAt() {
        return lastPlayerStateSyncAt;
    }

    public static String lastPlayerStateSyncSource() {
        return lastPlayerStateSyncSource;
    }

    public static String lastPlayerStateSyncError() {
        return lastPlayerStateSyncError;
    }

    public static void markPlayerStateSyncError(String error) {
        lastPlayerStateSyncError = error == null ? "" : error;
        if (!lastPlayerStateSyncError.isBlank()) {
            System.err.println("[EltenaAddon] player_state sync error: " + lastPlayerStateSyncError);
        }
    }

    public static String lastAbilityStateSyncAt() {
        return lastAbilityStateSyncAt;
    }

    public static String lastAbilityStateSyncSource() {
        return lastAbilityStateSyncSource;
    }

    public static String lastAbilityStateSyncError() {
        return lastAbilityStateSyncError;
    }

    public static int lastAbilityStateUnlockedCount() {
        return lastAbilityStateUnlockedCount;
    }

    public static void markAbilityStateSyncError(String error) {
        lastAbilityStateSyncError = error == null ? "" : error;
    }

    public static String getPlayerName() {
        PlayerStatePayload payload = getPlayerState();
        return payload != null && payload.playerName() != null && !payload.playerName().isBlank() ? payload.playerName() : "Player";
    }

    public static String getCurrentJobName() {
        PlayerStatePayload payload = getPlayerState();
        return payload != null && payload.currentJobName() != null && !payload.currentJobName().isBlank() ? payload.currentJobName() : "見習い";
    }

    public static int getLevel() {
        PlayerStatePayload payload = getPlayerState();
        return payload != null ? payload.level() : 1;
    }

    public static long getExp() {
        PlayerStatePayload payload = getPlayerState();
        return payload != null ? payload.exp() : 0L;
    }

    public static long getMaxExp() {
        PlayerStatePayload payload = getPlayerState();
        return payload != null ? Math.max(1L, payload.maxExp()) : 100L;
    }

    public static int getHp() {
        PlayerStatePayload payload = getPlayerState();
        return payload != null ? payload.hp() : 20;
    }

    public static int getMaxHp() {
        PlayerStatePayload payload = getPlayerState();
        return payload != null ? Math.max(1, payload.maxHp()) : 20;
    }

    public static int getMp() {
        PlayerStatePayload payload = getPlayerState();
        return payload != null ? payload.mp() : 10;
    }

    public static int getMaxMp() {
        PlayerStatePayload payload = getPlayerState();
        return payload != null ? Math.max(1, payload.maxMp()) : 20;
    }

    public static int getAttack() {
        PlayerStatePayload payload = getPlayerState();
        return payload != null ? payload.attack() : 0;
    }

    public static int getBaseAttack() {
        PlayerStatePayload payload = getPlayerState();
        if (payload == null) {
            return 0;
        }
        return payload.baseAttack() > 0 ? payload.baseAttack() : payload.attack();
    }

    public static double getWeaponAttack() {
        PlayerStatePayload payload = getPlayerState();
        return payload != null ? payload.weaponAttack() : 0.0D;
    }

    public static double getAttackPower() {
        PlayerStatePayload payload = getPlayerState();
        return payload != null ? payload.attackPower() : 0.0D;
    }

    public static double getDefense() {
        PlayerStatePayload payload = getPlayerState();
        return payload != null ? payload.defense() : 0.0D;
    }

    public static String getWeaponType() {
        PlayerStatePayload payload = getPlayerState();
        if (payload == null || payload.weaponType() == null) {
            return "";
        }
        return payload.weaponType().trim();
    }

    public static String getWeaponCategory() {
        PlayerStatePayload payload = getPlayerState();
        if (payload == null || payload.weaponCategory() == null) {
            return "";
        }
        return payload.weaponCategory().trim();
    }

    public static int getSkillPoint() {
        SkillTreePayload payload = getSkillTreeState();
        if (payload != null) {
            return Math.max(0, payload.skillPoint());
        }
        PlayerStatePayload playerState = getPlayerState();
        return playerState == null ? 0 : Math.max(0, playerState.skillPoint());
    }

    public static String getCurrentTitleName() {
        PlayerStatePayload payload = getPlayerState();
        if (payload == null || payload.currentTitleName() == null) {
            return "";
        }
        String value = payload.currentTitleName().trim();
        if (value.isBlank() || "none".equalsIgnoreCase(value) || "なし".equals(value)) {
            return "";
        }
        return value;
    }

    public static String getWorldTitleName() {
        PlayerStatePayload payload = getPlayerState();
        if (payload == null || payload.worldRankName() == null) {
            return "";
        }
        return payload.worldRankName().trim();
    }

    public static long getWorldTitleExp() {
        PlayerStatePayload payload = getPlayerState();
        return payload == null ? 0L : Math.max(0L, payload.worldRankExp());
    }

    public static int getUnlockedTitleCount() {
        SkillTreePayload payload = getSkillTreeState();
        if (payload != null && payload.unlockedTitleNames() != null && !payload.unlockedTitleNames().isEmpty()) {
            return payload.unlockedTitleNames().size();
        }
        return getCurrentTitleName().isBlank() ? 0 : 1;
    }

    public static double getAbilityCooldownRemaining(AbilitySummaryPayload summary) {
        return summary == null ? 0.0D : countdown(summary.cooldownRemaining(), lastAbilityStateReceivedAtMillis);
    }

    public static double getAbilityCooldownRemaining(AbilityRadialSlotPayload slot) {
        return slot == null ? 0.0D : countdown(slot.cooldownRemaining(), lastAbilityStateReceivedAtMillis);
    }

    public static void resetAllSyncState(String reason) {
        LATEST_PAYLOADS.clear();
        lastSkillTreeSyncAt = "";
        lastSkillTreeNodeCount = 0;
        lastSkillTreeSyncSource = "";
        lastSkillTreeSyncError = "";
        lastPlayerStateSyncAt = "";
        lastPlayerStateSyncSource = "";
        lastPlayerStateSyncError = "";
        lastAbilityStateSyncAt = "";
        lastAbilityStateSyncSource = "";
        lastAbilityStateSyncError = "";
        lastAbilityStateUnlockedCount = 0;
        lastAbilityStateReceivedAtMillis = 0L;
        if (isDebugEnabled()) {
            System.out.println("[EltenaAddon] Reset synced cache: reason=" + (reason == null ? "" : reason));
        }
    }

    private static boolean isDebugEnabled() {
        return Boolean.getBoolean(DEBUG_PROPERTY) || Boolean.getBoolean(LEGACY_DEBUG_PROPERTY);
    }

    private static double countdown(double syncedRemaining, long receivedAtMillis) {
        if (!Double.isFinite(syncedRemaining) || syncedRemaining <= 0.0D) {
            return 0.0D;
        }
        if (receivedAtMillis <= 0L) {
            return syncedRemaining;
        }
        double elapsedSeconds = Math.max(0.0D, (System.currentTimeMillis() - receivedAtMillis) / 1000.0D);
        return Math.max(0.0D, syncedRemaining - elapsedSeconds);
    }
}
