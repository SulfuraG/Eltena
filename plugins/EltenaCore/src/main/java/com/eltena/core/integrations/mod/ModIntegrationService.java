package com.eltena.core.integrations.mod;

import com.eltena.core.application.ability.AbilitySystem;
import com.eltena.core.application.equipment.MmoItemsEquipmentBonuses;
import com.eltena.core.application.equipment.WeaponAttackSnapshotResolver;
import com.eltena.core.application.logging.CoreLoggingSettings;
import com.eltena.core.application.player.PlayerManaService;
import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.ability.AbilityDefinition;
import com.eltena.core.domain.player.FinalPlayerStats;
import com.eltena.core.domain.player.PlayerProfile;
import com.eltena.core.domain.skill.SkillDefinition;
import com.eltena.core.domain.stats.PlayerStats;
import com.eltena.core.domain.title.TitleDefinition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ModIntegrationService {

    private final ServiceRegistry services;
    private final ModIntegrationSettings settings;
    private final ModPayloadRegistry registry;
    private final ModPayloadSerializer serializer;

    public ModIntegrationService(
        ServiceRegistry services,
        ModIntegrationSettings settings,
        ModPayloadRegistry registry,
        ModPayloadSerializer serializer
    ) {
        this.services = Objects.requireNonNull(services, "services");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        registerDefaultPayloads();
    }

    public int reload() {
        settings.reload();
        return registry.channels().size();
    }

    public ModIntegrationSettings settings() {
        return settings;
    }

    public List<ModSyncChannel> channels() {
        return List.copyOf(registry.channels());
    }

    public boolean isEnabled() {
        return settings.enabled();
    }

    public boolean isChannelEnabled(ModSyncChannel channel) {
        return settings.channelEnabled(channel);
    }

    public List<String> statusLines() {
        List<String> lines = new ArrayList<>();
        lines.add("Enabled: " + yesNo(settings.enabled()));
        lines.add("Client mod required: " + yesNo(settings.requireClientMod()));
        lines.add("Sync debug: " + yesNo(settings.syncDebug()));
        lines.add("Protocol version: " + settings.protocolVersion());
        lines.add("UI sync: " + yesNo(settings.allowUiSync()));
        lines.add("Skill sync: " + yesNo(settings.allowSkillSync()));
        lines.add("Registered channels: " + registry.channels().size());
        return lines;
    }

    public List<String> debugLines() {
        List<String> lines = new ArrayList<>(statusLines());
        lines.add("Channels:");
        for (ModSyncChannel channel : registry.channels()) {
            lines.add("- " + channel.displayName() + " (" + channel.id() + ") / enabled " + yesNo(isChannelEnabled(channel)));
        }
        lines.add("Authority: plugin authoritative / addon presentation and input");
        return lines;
    }

    public SyncResult syncPlayer(UUID playerId, String playerName) throws IOException {
        ModSyncContext context = loadContext(playerId, playerName);
        LinkedHashMap<ModSyncChannel, ModSyncEnvelope> envelopes = new LinkedHashMap<>();
        for (ModSyncChannel channel : registry.channels()) {
            if (!isChannelEnabled(channel)) {
                continue;
            }
            Map<String, Object> payload = registry.build(channel, context);
            envelopes.put(channel, new ModSyncEnvelope(channel, settings.protocolVersion(), Instant.now(), payload));
        }
        return new SyncResult(context, envelopes);
    }

    public String serialize(ModSyncEnvelope envelope) {
        return serializer.serialize(envelope);
    }

    public void syncVanillaLevelAndExp(Player player) throws IOException {
        if (player == null || !player.isOnline()) {
            return;
        }

        PlayerProfile profile = services.playerProfiles().loadOrCreate(player.getUniqueId(), player.getName());
        long requiredExp = services.levelSystem().requiredExpFor(profile.level());
        float progress = requiredExp > 0L
            ? Math.max(0.0F, Math.min(1.0F, profile.experience() / (float) requiredExp))
            : 0.0F;

        player.setLevel(Math.max(0, profile.level()));
        player.setExp(progress);

        if (CoreLoggingSettings.syncDebugEnabled(services.plugin())) {
            services.plugin().getLogger().info(
                "[EltenaCore] Synced vanilla EXP bar: "
                    + player.getName()
                    + " level=" + profile.level()
                    + " progress=" + profile.experience() + "/" + requiredExp
            );
        }
    }

    private ModSyncContext loadContext(UUID playerId, String playerName) throws IOException {
        PlayerProfile profile = services.playerProfiles().loadOrCreate(playerId, playerName);
        PlayerStats stats = services.playerStats().loadOrCreate(playerId);
        MmoItemsEquipmentBonuses equipmentBonuses = services.mmoItemsEquipmentStatsProvider().resolve(profile);
        FinalPlayerStats finalStats = services.finalPlayerStatsCalculator().calculate(profile, equipmentBonuses);
        return new ModSyncContext(profile, stats, finalStats, equipmentBonuses);
    }

    private void registerDefaultPayloads() {
        registry.register(ModSyncChannel.PLAYER_STATE, this::buildPlayerStatePayload);
        registry.register(ModSyncChannel.ABILITY_STATE, this::buildAbilityStatePayload);
        registry.register(ModSyncChannel.SKILL_TREE, this::buildSkillTreePayload);
    }

    private Map<String, Object> buildPlayerStatePayload(ModSyncContext context) {
        PlayerProfile profile = context.profile();
        PlayerStats stats = context.stats();
        FinalPlayerStats finalStats = context.finalStats();
        MmoItemsEquipmentBonuses equipmentBonuses = context.equipmentBonuses();
        PlayerManaService.ManaSnapshot manaSnapshot = resolveManaSnapshot(profile.playerId(), finalStats);
        Player onlinePlayer = Bukkit.getPlayer(profile.playerId());
        int calculatedMaxHp = services.finalPlayerStatsCalculator().calculateFinalMaxHp(stats, finalStats, equipmentBonuses);
        int finalMaxHp = resolveDisplayedMaxHp(onlinePlayer, calculatedMaxHp);
        int currentHp = resolveDisplayedCurrentHp(onlinePlayer, finalMaxHp);
        AttackDisplayState attackDisplay = resolveAttackDisplayState(profile, stats);
        int defenseValue = roundedAttackPower(finalStats.defense());
        String weaponType = resolveWeaponType(profile);
        String weaponCategory = resolveWeaponCategory(profile);
        long currentExp = profile.experience();
        long maxExp = services.levelSystem().requiredExpFor(profile.level());

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("player-id", profile.playerId().toString());
        payload.put("player-uuid", profile.playerId().toString());
        payload.put("player-name", profile.playerName());
        payload.put("level", profile.level());
        payload.put("experience", currentExp);
        payload.put("exp", currentExp);
        payload.put("max-exp", maxExp);
        payload.put("required-exp", maxExp);
        payload.put("current-job-id", profile.currentJobId());
        payload.put("current-job-name", services.jobSystem().displayName(profile.currentJobId()));
        payload.put("active-title-id", profile.activeTitleId());
        payload.put("active-title-name", services.titleSystem().displayName(profile.activeTitleId()));
        payload.put("current-title-id", profile.activeTitleId());
        payload.put("current-title-name", services.titleSystem().displayName(profile.activeTitleId()));
        payload.put("world-rank-id", profile.worldRankId());
        payload.put("world-rank-name", services.worldRankSystem().displayName(profile.worldRankId()));
        payload.put("world-rank-exp", profile.worldRankExperience());
        payload.put("skill-point", services.skillSystem().availableSkillPoints(profile));
        payload.put("plugin-authority", true);

        LinkedHashMap<String, Object> statPayload = new LinkedHashMap<>();
        statPayload.put("hp", currentHp);
        statPayload.put("max-hp", finalMaxHp);
        statPayload.put("mp", manaSnapshot.currentMana());
        statPayload.put("max-mp", manaSnapshot.maxMana());
        statPayload.put("attack", attackDisplay.baseAttack());
        statPayload.put("base-attack", attackDisplay.baseAttack());
        statPayload.put("weapon-attack", roundedAttackPower(attackDisplay.weaponAttack()));
        statPayload.put("attack-power", roundedAttackPower(attackDisplay.attackPower()));
        statPayload.put("defense", defenseValue);
        statPayload.put("weapon-type", weaponType);
        statPayload.put("weapon-category", weaponCategory);
        payload.put("stats", statPayload);
        payload.put("hp", currentHp);
        payload.put("max-hp", finalMaxHp);
        payload.put("mp", manaSnapshot.currentMana());
        payload.put("max-mp", manaSnapshot.maxMana());
        payload.put("attack", attackDisplay.baseAttack());
        payload.put("base-attack", attackDisplay.baseAttack());
        payload.put("weapon-attack", roundedAttackPower(attackDisplay.weaponAttack()));
        payload.put("attack-power", roundedAttackPower(attackDisplay.attackPower()));
        payload.put("defense", defenseValue);
        payload.put("weapon-type", weaponType);
        payload.put("weapon-category", weaponCategory);
        payload.put("item-type", weaponType);
        payload.put("item-category", weaponCategory);

        if (CoreLoggingSettings.mmoItemsDebugEnabled(services.plugin())) {
            services.plugin().getLogger().info(
                "[EltenaCore] player_state sync: "
                    + "player=" + profile.playerName()
                    + " baseAttack=" + attackDisplay.baseAttack()
                    + " weaponAttack=" + roundedAttackPower(attackDisplay.weaponAttack())
                    + " attackPower=" + roundedAttackPower(attackDisplay.attackPower())
                    + " hp=" + currentHp + "/" + finalMaxHp
                    + " defense=" + defenseValue
            );
        }
        return payload;
    }

    private Map<String, Object> buildAbilityStatePayload(ModSyncContext context) {
        PlayerProfile profile = context.profile();
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();

        List<Map<String, Object>> unlocked = new ArrayList<>();
        for (String abilityId : profile.unlockedAbilities()) {
            AbilityDefinition definition = services.abilitySystem().findDefinition(abilityId);
            if (definition == null) {
                services.plugin().getLogger().warning("[EltenaCore] ability_state skipped unknown unlocked ability id: " + abilityId);
                continue;
            }
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", definition.id());
            entry.put("display-name", definition.displayName());
            entry.put("description", definition.descriptionLines());
            entry.put("category", definition.category());
            entry.put("icon", definition.icon());
            entry.put("cooldown", definition.cooldownSeconds());
            entry.put("mp-cost", definition.cost().mp());
            entry.put("cooldown-remaining", services.abilitySystem().remainingCooldownSeconds(profile.playerId(), definition.id()));
            unlocked.add(entry);
        }
        payload.put("unlocked-abilities", unlocked);

        List<Map<String, Object>> equipped = new ArrayList<>();
        for (int slot = 1; slot <= AbilitySystem.SLOT_KEYS.size(); slot++) {
            String slotKey = services.abilitySystem().slotKey(slot);
            String abilityId = profile.equippedAbilities().getOrDefault(slotKey, "");
            AbilityDefinition definition = abilityId.isBlank() ? null : services.abilitySystem().findDefinition(abilityId);
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            entry.put("slot", slot);
            entry.put("key-name", switch (slot) {
                case 1 -> "Z";
                case 2 -> "X";
                case 3 -> "C";
                case 4 -> "V";
                default -> "?";
            });
            entry.put("ability-id", abilityId);
            entry.put("display-name", definition == null ? "" : definition.displayName());
            equipped.add(entry);
        }
        payload.put("equipped-slots", equipped);

        List<Map<String, Object>> radialSlots = new ArrayList<>();
        for (int slot = 0; slot < AbilitySystem.MAX_RADIAL_SLOTS; slot++) {
            String slotKey = services.abilitySystem().radialSlotKey(slot);
            String abilityId = profile.equippedAbilities().getOrDefault(slotKey, "");
            AbilityDefinition definition = abilityId.isBlank() ? null : services.abilitySystem().findDefinition(abilityId);
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            entry.put("slot", slot);
            entry.put("direction-label", Integer.toString(slot + 1));
            entry.put("ability-id", abilityId);
            entry.put("display-name", definition == null ? "" : definition.displayName());
            entry.put("icon", definition == null ? "" : definition.icon());
            entry.put("category", definition == null ? "" : definition.category());
            entry.put("cooldown", definition == null ? 0 : definition.cooldownSeconds());
            entry.put(
                "cooldown-remaining",
                definition == null ? 0L : services.abilitySystem().remainingCooldownSeconds(profile.playerId(), definition.id())
            );
            radialSlots.add(entry);
        }
        payload.put("radial-slots", radialSlots);
        payload.put("total-abilities", services.abilitySystem().listDefinitions().size());
        return payload;
    }

    private Map<String, Object> buildSkillTreePayload(ModSyncContext context) {
        PlayerProfile profile = context.profile();
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("current-job-id", profile.currentJobId());
        payload.put("current-job-name", services.jobSystem().displayName(profile.currentJobId()));
        payload.put("skill-point", services.skillSystem().availableSkillPoints(profile));

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (SkillDefinition definition : services.skillSystem().listDefinitions()) {
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", definition.id());
            entry.put("display-name", definition.displayName());
            entry.put("description", definition.descriptionLines());
            entry.put("category", definition.category());
            entry.put("icon", definition.icon());
            entry.put("current-rank", services.skillSystem().currentRank(profile, definition.id()));
            entry.put("max-rank", definition.maxRank());
            entry.put("required-points", definition.requiredPoints());
            entry.put("state", skillState(profile, definition));
            entry.put("prerequisites", definition.prerequisites());
            entry.put("requires-display", skillRequiresDisplay(definition));
            entry.put("position-x", definition.position().x());
            entry.put("position-y", definition.position().y());
            entry.put("effects-display", skillEffectsDisplay(definition));
            nodes.add(entry);
        }
        payload.put("nodes", nodes);

        List<Map<String, Object>> titles = new ArrayList<>();
        for (TitleDefinition definition : services.titleSystem().listTitles()) {
            if (!profile.unlockedTitles().contains(definition.id())) {
                continue;
            }
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", definition.id());
            entry.put("display-name", definition.displayName());
            entry.put("active", profile.activeTitleId().equals(definition.id()));
            titles.add(entry);
        }
        payload.put("titles", titles);
        return payload;
    }

    private PlayerManaService.ManaSnapshot resolveManaSnapshot(UUID playerId, FinalPlayerStats finalStats) {
        try {
            return services.playerManaService().resolve(playerId, finalStats);
        } catch (IOException exception) {
            services.plugin().getLogger().warning("[EltenaCore] Failed to resolve MP for player_state: " + exception.getMessage());
            return new PlayerManaService.ManaSnapshot(20, 20);
        }
    }

    private AttackDisplayState resolveAttackDisplayState(PlayerProfile profile, PlayerStats stats) {
        int baseAttack = stats == null ? 0 : stats.attack();
        if (profile == null) {
            return new AttackDisplayState(baseAttack, 0.0D, Math.max(1.0D, Math.round(baseAttack)));
        }
        Player player = Bukkit.getPlayer(profile.playerId());
        if (player == null || !player.isOnline()) {
            return new AttackDisplayState(baseAttack, 0.0D, Math.max(1.0D, Math.round(baseAttack)));
        }
        WeaponAttackSnapshotResolver.WeaponAttackSnapshot snapshot =
            services.weaponAttackSnapshotResolver().resolvePreview(player, baseAttack);
        double attackPower = Math.max(1.0D, Math.round(snapshot.baseAttack() + snapshot.weaponAttack()));
        AttackDisplayState state = new AttackDisplayState(
            snapshot.baseAttack(),
            snapshot.weaponAttack(),
            attackPower
        );
        if (services.growthSettings().combatDebugEnabled()) {
            services.growthSettings().combatDebugLog(
                "player_state display attack: player=" + player.getName()
                    + " baseAttack=" + state.baseAttack()
                    + " weaponAttack=" + state.weaponAttack()
                    + " attackPower=" + state.attackPower()
            );
        }
        return state;
    }

    private int resolveDisplayedCurrentHp(Player player, int fallbackMaxHp) {
        if (player == null || !player.isOnline()) {
            return fallbackMaxHp;
        }
        return Math.max(0, Math.min(fallbackMaxHp, roundedAttackPower(player.getHealth())));
    }

    private int resolveDisplayedMaxHp(Player player, int fallbackMaxHp) {
        if (player == null || !player.isOnline()) {
            return fallbackMaxHp;
        }
        org.bukkit.attribute.Attribute attributeType = resolveMaxHealthAttribute();
        if (attributeType == null) {
            return fallbackMaxHp;
        }
        var attribute = player.getAttribute(attributeType);
        if (attribute == null) {
            return fallbackMaxHp;
        }
        return Math.max(1, roundedAttackPower(attribute.getValue()));
    }

    private org.bukkit.attribute.Attribute resolveMaxHealthAttribute() {
        for (String candidate : new String[]{"MAX_HEALTH", "GENERIC_MAX_HEALTH"}) {
            try {
                return org.bukkit.attribute.Attribute.valueOf(candidate);
            } catch (IllegalArgumentException ignored) {
                // Try the next compatible runtime name.
            }
        }
        return null;
    }

    private String resolveWeaponType(PlayerProfile profile) {
        if (profile == null) {
            return "";
        }
        Player player = Bukkit.getPlayer(profile.playerId());
        if (player == null || !player.isOnline()) {
            return "";
        }
        String key = services.weaponTypeResolver()
            .resolve(player.getInventory().getItemInMainHand())
            .name()
            .toLowerCase();
        return services.messages().get("command.weapon-type." + key);
    }

    private String resolveWeaponCategory(PlayerProfile profile) {
        if (profile == null) {
            return "";
        }
        Player player = Bukkit.getPlayer(profile.playerId());
        if (player == null || !player.isOnline()) {
            return "";
        }
        String category = services.mmoItemsEquipmentStatsProvider().resolveWeaponCategory(player.getInventory().getItemInMainHand());
        return category == null ? "" : category;
    }

    private int roundedAttackPower(double attackPower) {
        return Math.max(0, (int) Math.round(attackPower));
    }

    private List<String> skillRequiresDisplay(SkillDefinition definition) {
        List<String> display = new ArrayList<>();
        if (!definition.requirements().all().isEmpty()) {
            display.add("all: " + definition.requirements().all().stream().map(services.displayNameResolver()::skillName).toList());
        }
        if (!definition.requirements().any().isEmpty()) {
            display.add("any: " + definition.requirements().any().stream().map(services.displayNameResolver()::skillName).toList());
        }
        definition.requirements().ranks().forEach((id, rank) ->
            display.add(services.displayNameResolver().skillName(id) + " rank >= " + rank)
        );
        return display;
    }

    private List<String> skillEffectsDisplay(SkillDefinition definition) {
        List<String> display = new ArrayList<>();
        definition.effects().forEach((id, values) -> display.add(formatEffectDisplay(id, values)));
        definition.unlockAbilities().forEach(ability -> display.add("unlock ability: " + ability));
        return display;
    }

    private String formatEffectDisplay(String effectId, Map<String, Object> values) {
        String type = stringValue(values.get("type"), "raw");
        if (values.containsKey("value-per-rank")) {
            return effectId + " (" + type + ") +" + values.get("value-per-rank") + "/rank";
        }
        if (values.containsKey("value")) {
            return effectId + " (" + type + ") +" + values.get("value");
        }
        if (values.containsKey("ability-id")) {
            return effectId + " unlocks " + values.get("ability-id");
        }
        if (values.containsKey("description-only")) {
            return effectId + ": " + values.get("description-only");
        }
        return effectId;
    }

    private String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private String skillState(PlayerProfile profile, SkillDefinition definition) {
        int currentRank = services.skillSystem().currentRank(profile, definition.id());
        if (currentRank > 0) {
            return "learned";
        }
        if (services.skillSystem().availableSkillPoints(profile) < definition.requiredPoints()) {
            return "locked";
        }
        return services.skillSystem().missingPrerequisites(profile, definition).isEmpty() ? "available" : "locked";
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    public record SyncResult(
        ModSyncContext context,
        Map<ModSyncChannel, ModSyncEnvelope> envelopes
    ) {
    }

    private record AttackDisplayState(
        int baseAttack,
        double weaponAttack,
        double attackPower
    ) {
    }
}
