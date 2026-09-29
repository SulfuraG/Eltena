package com.eltena.core.application.ability;

import com.eltena.core.application.logging.CoreLoggingSettings;
import com.eltena.core.application.player.PlayerManaService;
import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.ability.AbilityDefinition;
import com.eltena.core.domain.player.FinalPlayerStats;
import com.eltena.core.domain.player.PlayerProfile;
import com.eltena.core.domain.stats.WeaponType;
import io.lumine.mythic.api.MythicPlugin;
import io.lumine.mythic.api.MythicProvider;
import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;

public final class AbilitySystem {
    public static final List<String> SLOT_KEYS = List.of("slot-1", "slot-2", "slot-3", "slot-4");
    public static final int MAX_RADIAL_SLOTS = 8;
    private static final String RADIAL_SLOT_PREFIX = "radial-";

    private final ServiceRegistry services;
    private final AbilityCatalog catalog;
    private final AbilityLoader loader;
    private final Map<UUID, Map<String, Instant>> cooldowns = new ConcurrentHashMap<>();

    public AbilitySystem(ServiceRegistry services, AbilityCatalog catalog, AbilityLoader loader) {
        this.services = Objects.requireNonNull(services, "services");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    public int reload() {
        return loader.loadInto(catalog);
    }

    public List<AbilityDefinition> listDefinitions() {
        return catalog.list();
    }

    public AbilityDefinition findDefinition(String abilityId) {
        return catalog.find(normalizeAbilityId(abilityId));
    }

    public AbilityDefinition requireDefinition(String abilityId) {
        String normalizedAbilityId = normalizeAbilityId(abilityId);
        AbilityDefinition definition = catalog.find(normalizedAbilityId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown ability: " + normalizedAbilityId);
        }
        return definition;
    }

    public double resolveEquippedDamageMultiplier(PlayerProfile profile) {
        if (profile == null) {
            return 1.0D;
        }
        double multiplier = 1.0D;
        for (String abilityId : profile.equippedAbilityIds()) {
            AbilityDefinition definition = catalog.find(normalizeAbilityId(abilityId));
            if (definition == null) {
                continue;
            }
            double configured = asDouble(definition.effect("damage-multiplier").get("value"), 1.0D);
            if (configured > 0.0D) {
                multiplier *= configured;
            }
        }
        return multiplier;
    }

    public boolean isUnlocked(PlayerProfile profile, String abilityId) {
        String normalizedAbilityId = normalizeAbilityId(abilityId);
        return normalizedAbilityId != null && profile.unlockedAbilities().contains(normalizedAbilityId);
    }

    public Set<String> unlockAbilities(PlayerProfile profile, List<String> abilityIds) {
        if (abilityIds == null || abilityIds.isEmpty()) {
            return Set.of();
        }
        Set<String> unlocked = new LinkedHashSet<>();
        for (String abilityId : abilityIds) {
            String normalizedAbilityId = normalizeAbilityId(abilityId);
            AbilityDefinition definition = catalog.find(normalizedAbilityId);
            if (definition == null) {
                continue;
            }
            if (!missingSkillRequirements(profile, definition).isEmpty()) {
                continue;
            }
            unlocked.add(definition.id());
        }
        return unlocked;
    }

    public PlayerProfile unlockAbility(PlayerProfile profile, String abilityId) {
        String normalizedAbilityId = normalizeAbilityId(abilityId);
        AbilityDefinition definition = catalog.find(normalizedAbilityId);
        if (definition == null || isUnlocked(profile, normalizedAbilityId)) {
            return profile;
        }
        PlayerProfile updated = profile.withAddedUnlockedAbilities(Set.of(definition.id()));
        LinkedHashMap<String, String> equipped = new LinkedHashMap<>(updated.equippedAbilities());
        if (!equipped.containsValue(definition.id())) {
            for (String slotKey : SLOT_KEYS) {
                if (equipped.getOrDefault(slotKey, "").isBlank()) {
                    equipped.put(slotKey, definition.id());
                    break;
                }
            }
        }
        return updated.withEquippedAbilities(equipped);
    }

    public PlayerProfile applyUnlocks(PlayerProfile profile, List<String> abilityIds) {
        Set<String> unlocks = unlockAbilities(profile, abilityIds);
        if (unlocks.isEmpty()) {
            return profile;
        }
        PlayerProfile updated = profile.withAddedUnlockedAbilities(unlocks);
        LinkedHashMap<String, String> equipped = new LinkedHashMap<>(updated.equippedAbilities());
        for (String abilityId : unlocks) {
            if (equipped.containsValue(abilityId)) {
                continue;
            }
            for (String slotKey : SLOT_KEYS) {
                if (equipped.getOrDefault(slotKey, "").isBlank()) {
                    equipped.put(slotKey, abilityId);
                    break;
                }
            }
        }
        return updated.withEquippedAbilities(equipped);
    }

    public PlayerProfile equipAbility(PlayerProfile profile, String slot, String abilityId) {
        if (slot == null || slot.isBlank() || abilityId == null || abilityId.isBlank()) {
            return profile;
        }
        if (!isAssignableSlot(slot)) {
            return profile;
        }
        String normalizedAbilityId = normalizeAbilityId(abilityId);
        if (!isUnlocked(profile, normalizedAbilityId)) {
            return profile;
        }
        LinkedHashMap<String, String> equipped = new LinkedHashMap<>(profile.equippedAbilities());
        equipped.put(slot, normalizedAbilityId);
        return profile.withEquippedAbilities(equipped);
    }

    public PlayerProfile unequipAbility(PlayerProfile profile, String slot) {
        if (slot == null || slot.isBlank()) {
            return profile;
        }
        if (!isAssignableSlot(slot)) {
            return profile;
        }
        LinkedHashMap<String, String> equipped = new LinkedHashMap<>(profile.equippedAbilities());
        equipped.put(slot, "");
        return profile.withEquippedAbilities(equipped);
    }

    public String slotKey(int slot) {
        if (slot < 1 || slot > SLOT_KEYS.size()) {
            return "";
        }
        return SLOT_KEYS.get(slot - 1);
    }

    public String radialSlotKey(int slot) {
        if (slot < 0 || slot >= MAX_RADIAL_SLOTS) {
            return "";
        }
        return RADIAL_SLOT_PREFIX + slot;
    }

    public String equippedAbilityId(PlayerProfile profile, int slot) {
        if (profile == null) {
            return "";
        }
        String slotKey = slotKey(slot);
        if (slotKey.isBlank()) {
            return "";
        }
        return normalizeAbilityId(profile.equippedAbilities().getOrDefault(slotKey, ""));
    }

    public String radialAbilityId(PlayerProfile profile, int slot) {
        if (profile == null) {
            return "";
        }
        String slotKey = radialSlotKey(slot);
        if (slotKey.isBlank()) {
            return "";
        }
        return normalizeAbilityId(profile.equippedAbilities().getOrDefault(slotKey, ""));
    }

    public long remainingCooldownSeconds(UUID playerId, String abilityId) {
        Map<String, Instant> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) {
            return 0L;
        }
        Instant readyAt = playerCooldowns.get(abilityId);
        if (readyAt == null) {
            return 0L;
        }
        long seconds = java.time.Duration.between(Instant.now(), readyAt).getSeconds();
        if (java.time.Duration.between(Instant.now(), readyAt).toMillisPart() > 0) {
            seconds += 1L;
        }
        return Math.max(0L, seconds);
    }

    public void resetPlayerState(UUID playerId) {
        if (playerId == null) {
            return;
        }
        cooldowns.remove(playerId);
    }

    public AbilityExecutionResult execute(Player player, String abilityId) throws IOException {
        if (player == null) {
            return denied(null, abilityId, "missing-player");
        }
        String normalizedAbilityId = normalizeAbilityId(abilityId);
        AbilityDefinition definition = catalog.find(normalizedAbilityId);
        if (definition == null) {
            return denied(player, loadProfile(player), normalizedAbilityId, "unknown-ability");
        }

        PlayerProfile profile = loadProfile(player);
        FinalPlayerStats finalStats = services.finalPlayerStatsCalculator().calculate(profile);
        if (!isUnlocked(profile, normalizedAbilityId)) {
            return denied(player, profile, normalizedAbilityId, "locked");
        }

        List<String> missingSkills = missingSkillRequirements(profile, definition);
        if (!missingSkills.isEmpty()) {
            return denied(player, profile, normalizedAbilityId, "missing-skill-requirements");
        }

        WeaponCheck weaponCheck = resolveWeaponCheck(player, definition);
        if (!weaponCheck.allowed()) {
            return denied(player, profile, normalizedAbilityId, AbilityExecutionResult.INVALID_WEAPON
                + " required=" + String.join(",", definition.weaponTypes())
                + " actual=" + weaponCheck.actualType());
        }

        int effectiveCooldownSeconds = resolveCooldownSeconds(definition, finalStats);
        if (isOnCooldown(player.getUniqueId(), normalizedAbilityId, effectiveCooldownSeconds)) {
            return denied(player, profile, normalizedAbilityId, "cooldown");
        }

        PlayerManaService.ManaSnapshot mana = resolveManaSnapshot(player, finalStats);
        if (mana.currentMana() < definition.cost().mp()) {
            return denied(player, profile, normalizedAbilityId, "NO_MP");
        }

        String executionFailure = delegateExecution(player, definition);
        if (executionFailure != null) {
            return denied(player, profile, normalizedAbilityId, executionFailure);
        }

        PlayerManaService.ManaSnapshot afterMana = consumeMana(player, definition.cost().mp(), mana, finalStats);
        markCooldown(player.getUniqueId(), normalizedAbilityId, effectiveCooldownSeconds);
        notifyAbilityUsed(player, definition);
        if (CoreLoggingSettings.debugEnabled(services.plugin())) {
            services.plugin().getLogger().info(
                "[EltenaCore] Ability used: " + player.getName()
                    + " -> " + normalizedAbilityId
                    + " mp=" + afterMana.currentMana() + "/" + afterMana.maxMana()
                    + " cooldown=" + effectiveCooldownSeconds
            );
        }
        return new AbilityExecutionResult(profile, true, normalizedAbilityId, "", List.of("ability executed"));
    }

    public AbilityExecutionResult useAbility(Player player, String abilityId) throws IOException {
        return execute(player, abilityId);
    }

    public void logMythicAvailabilityOnStartup() {
        MythicAvailability availability = resolveMythicAvailability();
        services.plugin().getLogger().info(
            "[EltenaCore] MythicMobs plugin detected: enabled=" + availability.pluginEnabled()
                + " version=" + availability.version()
        );
        services.plugin().getLogger().info(
            "[EltenaCore] MythicBukkit API available: " + availability.apiAvailable()
        );
        if (!availability.available()) {
            services.plugin().getLogger().warning(
                "[EltenaCore] Mythic availability detail: plugin=" + availability.pluginPresent()
                    + " enabled=" + availability.pluginEnabled()
                    + " api=" + availability.apiAvailable()
                    + " helper=" + availability.helperAvailable()
                    + " reason=" + availability.reason()
            );
        }
    }

    private PlayerProfile loadProfile(Player player) throws IOException {
        return services.playerProfiles().loadOrCreate(player.getUniqueId(), player.getName());
    }

    private List<String> missingSkillRequirements(PlayerProfile profile, AbilityDefinition definition) {
        List<String> missing = new ArrayList<>();
        for (String skillId : definition.requires().skills()) {
            if (services.skillSystem().currentRank(profile, skillId) <= 0) {
                missing.add(skillId);
            }
        }
        return missing;
    }

    private WeaponCheck resolveWeaponCheck(Player player, AbilityDefinition definition) {
        List<String> requiredTypes = definition.weaponTypes();
        if (requiredTypes.isEmpty()) {
            return new WeaponCheck(true, "any");
        }
        for (String requiredType : requiredTypes) {
            if ("any".equalsIgnoreCase(requiredType)) {
                return new WeaponCheck(true, "any");
            }
        }

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        String actualType = toAbilityWeaponType(services.weaponTypeResolver().resolve(heldItem));
        boolean allowed = false;
        for (String requiredType : requiredTypes) {
            if (requiredType.equalsIgnoreCase(actualType)) {
                allowed = true;
                break;
            }
        }
        return new WeaponCheck(allowed, actualType);
    }

    private String toAbilityWeaponType(WeaponType type) {
        if (type == null) {
            return "other";
        }
        return switch (type) {
            case SWORD -> "sword";
            case GREATSWORD -> "greatsword";
            case AXE -> "axe";
            case SPEAR -> "spear";
            case BOW -> "bow";
            case STAFF -> "staff";
            case DAGGER -> "dagger";
            case FIST -> "unarmed";
            case OTHER -> "other";
        };
    }

    private boolean isOnCooldown(UUID playerId, String abilityId, int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return false;
        }
        Map<String, Instant> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) {
            return false;
        }
        Instant readyAt = playerCooldowns.get(abilityId);
        return readyAt != null && readyAt.isAfter(Instant.now());
    }

    private boolean isAssignableSlot(String slot) {
        return SLOT_KEYS.contains(slot) || isRadialSlot(slot);
    }

    private boolean isRadialSlot(String slot) {
        if (slot == null || !slot.startsWith(RADIAL_SLOT_PREFIX)) {
            return false;
        }
        try {
            int index = Integer.parseInt(slot.substring(RADIAL_SLOT_PREFIX.length()));
            return index >= 0 && index < MAX_RADIAL_SLOTS;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private void markCooldown(UUID playerId, String abilityId, int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return;
        }
        cooldowns.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
            .put(normalizeAbilityId(abilityId), Instant.now().plusSeconds(cooldownSeconds));
    }

    private String delegateExecution(Player player, AbilityDefinition definition) {
        String executorType = definition.executor().type();
        if ("builtin".equalsIgnoreCase(executorType)) {
            return executeBuiltIn(player, definition)
                ? null
                : AbilityExecutionResult.BUILTIN_EXECUTE_FAILED;
        }
        if ("builtin_effects".equalsIgnoreCase(executorType)) {
            return executeBuiltInEffects(player, definition)
                ? null
                : AbilityExecutionResult.BUILTIN_EXECUTE_FAILED;
        }
        if ("mythicmobs".equalsIgnoreCase(executorType)) {
            return executeMythicMobSkill(player, definition)
                ? null
                : AbilityExecutionResult.MYTHIC_EXECUTE_FAILED;
        }
        return AbilityExecutionResult.MYTHIC_EXECUTE_FAILED;
    }

    private boolean executeMythicMobSkill(Player player, AbilityDefinition definition) {
        String skillName = definition.executor().skill();
        if (skillName == null || skillName.isBlank()) {
            services.plugin().getLogger().warning(
                "[EltenaCore] Mythic ability failed: " + player.getName()
                    + " -> " + skillName
                    + " reason=missing-skill-name"
            );
            return false;
        }
        MythicAvailability availability = resolveMythicAvailability();
        if (CoreLoggingSettings.debugEnabled(services.plugin())) {
            services.plugin().getLogger().info(
                "[EltenaCore] Mythic availability: plugin=" + availability.pluginPresent()
                    + " api=" + availability.apiAvailable()
                    + " helper=" + availability.helperAvailable()
            );
        }
        if (!availability.available()) {
            services.plugin().getLogger().warning(
                "[EltenaCore] Mythic ability failed: " + player.getName()
                    + " -> " + skillName
                    + " reason=" + availability.reason()
            );
            return false;
        }
        try {
            boolean cast = availability.helper().castSkill(player, skillName);
            if (!cast) {
                services.plugin().getLogger().warning(
                    "[EltenaCore] Mythic ability failed: " + player.getName()
                        + " -> " + skillName
                        + " reason=cast-returned-false"
                );
                return false;
            }
            if (CoreLoggingSettings.debugEnabled(services.plugin())) {
                services.plugin().getLogger().info(
                    "[EltenaCore] Mythic ability cast: " + player.getName()
                        + " -> " + skillName
                );
            }
            return true;
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
            services.plugin().getLogger().warning(
                "[EltenaCore] Mythic ability failed: " + player.getName()
                    + " -> " + skillName
                    + " reason=" + message
            );
            return false;
        }
    }

    private MythicAvailability resolveMythicAvailability() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (plugin == null) {
            return MythicAvailability.unavailable(false, false, "", AbilityExecutionResult.MYTHIC_PLUGIN_MISSING);
        }
        if (!plugin.isEnabled()) {
            return MythicAvailability.unavailable(true, false, plugin.getDescription().getVersion(), AbilityExecutionResult.MYTHIC_PLUGIN_DISABLED);
        }
        try {
            MythicPlugin api = resolveMythicPlugin(plugin);
            if (api == null) {
                return MythicAvailability.unavailable(true, true, plugin.getDescription().getVersion(), AbilityExecutionResult.MYTHIC_API_UNAVAILABLE);
            }
            BukkitAPIHelper helper = resolveApiHelper(api);
            if (helper == null) {
                return MythicAvailability.unavailable(true, true, plugin.getDescription().getVersion(), AbilityExecutionResult.MYTHIC_HELPER_UNAVAILABLE);
            }
            return new MythicAvailability(true, true, true, true, plugin.getDescription().getVersion(), "", api, helper);
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
            return MythicAvailability.unavailable(true, true, plugin.getDescription().getVersion(), "api-exception:" + message);
        }
    }

    private MythicPlugin resolveMythicPlugin(Plugin plugin) {
        if (plugin instanceof MythicPlugin mythicPlugin) {
            return mythicPlugin;
        }
        MythicBukkit mythicBukkit = MythicBukkit.inst();
        if (mythicBukkit != null) {
            return mythicBukkit;
        }
        return MythicProvider.get();
    }

    private BukkitAPIHelper resolveApiHelper(MythicPlugin api) {
        if (api instanceof MythicBukkit mythicBukkit) {
            return mythicBukkit.getAPIHelper();
        }
        try {
            Method method = api.getClass().getMethod("getAPIHelper");
            Object value = method.invoke(api);
            return value instanceof BukkitAPIHelper helper ? helper : null;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            return null;
        }
    }

    private boolean executeBuiltIn(Player player, AbilityDefinition definition) {
        String action = definition.executor().action();
        if ("quick_step".equalsIgnoreCase(action)) {
            return executeQuickStepBuiltin(player);
        }
        if ("diva_lovesong".equalsIgnoreCase(action)) {
            return executeDivaLoveSongBuiltin(player);
        }
        return false;
    }

    private boolean executeBuiltInEffects(Player player, AbilityDefinition definition) {
        Map<String, Object> meta = definition.executor().meta();
        List<Player> targets = resolveBuiltinEffectTargets(player, meta);
        if (targets.isEmpty()) {
            targets = List.of(player);
        }

        boolean executed = false;
        for (Map<String, Object> effect : asMapList(meta.get("effects"))) {
            PotionEffectType effectType = parsePotionEffectType(asString(effect.get("type")), definition.id());
            if (effectType == null) {
                continue;
            }
            int duration = Math.max(1, asInt(effect.get("duration"), 0));
            int amplifier = Math.max(0, asInt(effect.get("amplifier"), 0));
            PotionEffect potionEffect = new PotionEffect(effectType, duration, amplifier, false, false, true);
            for (Player target : targets) {
                target.removePotionEffect(effectType);
                target.addPotionEffect(potionEffect);
            }
            executed = true;
        }

        for (Map<String, Object> particle : asMapList(meta.get("particles"))) {
            Particle particleType = parseParticle(asString(particle.get("type")), definition.id());
            if (particleType == null) {
                continue;
            }
            int amount = Math.max(1, asInt(particle.get("amount"), 1));
            double offset = asDouble(particle.get("offset"), 0.3D);
            double extra = asDouble(particle.get("extra"), 0.02D);
            for (Player target : targets) {
                target.getWorld().spawnParticle(
                    particleType,
                    target.getLocation().clone().add(0.0D, 1.0D, 0.0D),
                    amount,
                    offset,
                    offset,
                    offset,
                    extra
                );
            }
            executed = true;
        }

        Map<String, Object> soundMeta = asMap(meta.get("sound"));
        if (!soundMeta.isEmpty()) {
            Sound soundType = parseSound(asString(soundMeta.get("type")), definition.id());
            if (soundType != null) {
                float volume = (float) asDouble(soundMeta.get("volume"), 1.0D);
                float pitch = (float) asDouble(soundMeta.get("pitch"), 1.0D);
                for (Player target : targets) {
                    target.playSound(target.getLocation(), soundType, volume, pitch);
                }
                executed = true;
            }
        }

        String message = asString(meta.get("message"));
        if (!message.isBlank()) {
            player.sendMessage(message);
            executed = true;
        }

        return executed;
    }

    private List<Player> resolveBuiltinEffectTargets(Player player, Map<String, Object> meta) {
        String configuredTarget = asString(meta.get("target"));
        String normalizedTarget = configuredTarget == null ? "" : configuredTarget.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedTarget) {
            case "nearby_players" -> {
                double radius = Math.max(1.0D, asDouble(meta.get("radius"), 8.0D));
                yield findNearbyPlayers(player, radius);
            }
            case "party_or_self", "self", "" -> List.of(player);
            default -> List.of(player);
        };
    }

    private boolean executeQuickStep(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false, true));
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0.0D, 0.4D, 0.0D), 20, 0.25D, 0.15D, 0.25D, 0.08D);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6F, 1.4F);
        return true;
    }

    private boolean executeQuickStepSafe(Player player) {
        try {
            PotionEffectType speedType = PotionEffectType.SPEED;
            if (speedType == null) {
                services.plugin().getLogger().warning("[EltenaCore] Builtin ability failed: quick_step reason=missing-speed-effect");
                return false;
            }
            player.removePotionEffect(speedType);
            player.addPotionEffect(new PotionEffect(speedType, 40, 1, false, false, true));
            var location = player.getLocation();
            player.getWorld().spawnParticle(Particle.CLOUD, location.clone().add(0.0D, 0.4D, 0.0D), 20, 0.3D, 0.1D, 0.3D, 0.02D);
            player.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6F, 1.4F);
            return true;
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null || exception.getMessage().isBlank() ? exception.getClass().getSimpleName() : exception.getMessage();
            services.plugin().getLogger().warning("[EltenaCore] Builtin ability failed: quick_step reason=" + message);
            return false;
        }
    }

    private boolean executeQuickStepBuiltin(Player player) {
        try {
            PotionEffectType speedType = PotionEffectType.SPEED;
            if (speedType == null) {
                services.plugin().getLogger().warning("[EltenaCore] Builtin ability failed: quick_step reason=missing-speed-effect");
                return false;
            }
            player.removePotionEffect(speedType);
            player.addPotionEffect(new PotionEffect(speedType, 40, 1, false, false, true));
            var location = player.getLocation();
            player.getWorld().spawnParticle(Particle.CLOUD, location.clone().add(0.0D, 0.4D, 0.0D), 20, 0.3D, 0.1D, 0.3D, 0.02D);
            player.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6F, 1.4F);
            return true;
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
            services.plugin().getLogger().warning("[EltenaCore] Builtin ability failed: quick_step reason=" + message);
            return false;
        }
    }

    private boolean executeDivaLoveSongBuiltin(Player player) {
        try {
            PotionEffectType regenerationType = PotionEffectType.REGENERATION;
            if (regenerationType == null) {
                services.plugin().getLogger().warning("[EltenaCore] Builtin ability failed: diva_lovesong reason=missing-regeneration-effect");
                return false;
            }
            player.removePotionEffect(regenerationType);
            player.addPotionEffect(new PotionEffect(regenerationType, 100, 1, false, false, true));
            var location = player.getLocation();
            player.getWorld().spawnParticle(Particle.HEART, location.clone().add(0.0D, 1.0D, 0.0D), 24, 0.6D, 0.5D, 0.6D, 0.02D);
            player.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
            if (CoreLoggingSettings.debugEnabled(services.plugin())) {
                services.plugin().getLogger().info("[EltenaCore] Builtin diva_lovesong applied to " + player.getName());
            }
            return true;
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
            services.plugin().getLogger().warning("[EltenaCore] Builtin ability failed: diva_lovesong reason=" + message);
            return false;
        }
    }

    private List<Player> findNearbyPlayers(Player player, double radius) {
        List<Player> targets = new ArrayList<>();
        double radiusSquared = radius * radius;
        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(player.getLocation()) <= radiusSquared) {
                targets.add(nearby);
            }
        }
        return targets;
    }

    private PotionEffectType parsePotionEffectType(String name, String abilityId) {
        if (name == null || name.isBlank()) {
            return null;
        }
        PotionEffectType resolved = PotionEffectType.getByName(name.trim().toUpperCase(Locale.ROOT));
        if (resolved == null) {
            services.plugin().getLogger().warning("[EltenaCore] builtin_effects skipped invalid effect type for " + abilityId + ": " + name);
        }
        return resolved;
    }

    private Particle parseParticle(String name, String abilityId) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            services.plugin().getLogger().warning("[EltenaCore] builtin_effects skipped invalid particle type for " + abilityId + ": " + name);
            return null;
        }
    }

    private Sound parseSound(String name, String abilityId) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Sound.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            services.plugin().getLogger().warning("[EltenaCore] builtin_effects skipped invalid sound type for " + abilityId + ": " + name);
            return null;
        }
    }

    private Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return normalized;
    }

    private List<Map<String, Object>> asMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object element : list) {
            Map<String, Object> map = asMap(element);
            if (!map.isEmpty()) {
                normalized.add(map);
            }
        }
        return normalized;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private double asDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private void notifyAbilityUsed(Player player, AbilityDefinition definition) {
        player.sendMessage(services.messages().get(
            "ability.used",
            "ability", definition.displayName()
        ));
    }

    private int resolveCooldownSeconds(AbilityDefinition definition, FinalPlayerStats finalStats) {
        if (definition == null) {
            return 0;
        }
        double reduction = finalStats == null ? 0.0D : finalStats.cooldownReduction();
        double multiplier = Math.max(0.5D, 1.0D - reduction);
        return Math.max(0, (int) Math.ceil(definition.cooldownSeconds() * multiplier));
    }

    private PlayerManaService.ManaSnapshot resolveManaSnapshot(Player player, FinalPlayerStats finalStats) {
        try {
            return services.playerManaService().resolve(player.getUniqueId(), finalStats);
        } catch (IOException exception) {
            services.plugin().getLogger().warning("[EltenaCore] Failed to resolve mana snapshot: " + exception.getMessage());
            return new PlayerManaService.ManaSnapshot(20, 20);
        }
    }

    private PlayerManaService.ManaSnapshot consumeMana(
        Player player,
        int amount,
        PlayerManaService.ManaSnapshot snapshot,
        FinalPlayerStats finalStats
    ) {
        if (amount <= 0) {
            return snapshot;
        }
        try {
            return services.playerManaService().consume(player.getUniqueId(), amount, finalStats);
        } catch (IOException exception) {
            services.plugin().getLogger().warning("[EltenaCore] Failed to persist mana consumption: " + exception.getMessage());
            return snapshot;
        }
    }

    private AbilityExecutionResult denied(PlayerProfile profile, String abilityId, String reason) {
        String normalizedAbilityId = normalizeAbilityId(abilityId);
        return new AbilityExecutionResult(profile, false, normalizedAbilityId == null ? "" : normalizedAbilityId, reason, List.of());
    }

    private AbilityExecutionResult denied(Player player, PlayerProfile profile, String abilityId, String reason) {
        notifyDenied(player, abilityId, reason);
        return denied(profile, abilityId, reason);
    }

    private void notifyDenied(Player player, String abilityId, String reason) {
        if (player == null) {
            return;
        }
        String message = denialMessage(player, abilityId, reason);
        if (message == null || message.isBlank()) {
            return;
        }
        player.sendMessage(message);
    }

    private String denialMessage(Player player, String abilityId, String reason) {
        if (reason == null || reason.isBlank()) {
            return services.messages().get("ability.denied.generic");
        }
        if ("NO_MP".equalsIgnoreCase(reason)) {
            return services.messages().get("ability.denied.no-mp");
        }
        if ("cooldown".equalsIgnoreCase(reason)) {
            long remaining = remainingCooldownSeconds(player.getUniqueId(), normalizeAbilityId(abilityId));
            return remaining > 0
                ? services.messages().get("ability.denied.cooldown-remaining", "seconds", remaining)
                : services.messages().get("ability.denied.cooldown");
        }
        if ("locked".equalsIgnoreCase(reason)) {
            return services.messages().get("ability.denied.locked");
        }
        if ("missing-skill-requirements".equalsIgnoreCase(reason)) {
            return services.messages().get("ability.denied.missing-skill-requirements");
        }
        if (reason.startsWith(AbilityExecutionResult.INVALID_WEAPON)) {
            return services.messages().get("ability.denied.invalid-weapon");
        }
        if (AbilityExecutionResult.MYTHIC_EXECUTE_FAILED.equalsIgnoreCase(reason)
            || AbilityExecutionResult.BUILTIN_EXECUTE_FAILED.equalsIgnoreCase(reason)) {
            return services.messages().get("ability.denied.execute-failed");
        }
        if ("unknown-ability".equalsIgnoreCase(reason)) {
            return services.messages().get("ability.denied.unknown");
        }
        return services.messages().get("ability.denied.generic");
    }

    private String normalizeAbilityId(String abilityId) {
        return AbilityCatalog.normalizeId(abilityId);
    }

    private record WeaponCheck(boolean allowed, String actualType) {
    }

    private record MythicAvailability(
        boolean pluginPresent,
        boolean pluginEnabled,
        boolean apiAvailable,
        boolean helperAvailable,
        String version,
        String reason,
        MythicPlugin api,
        BukkitAPIHelper helper
    ) {
        private static MythicAvailability unavailable(boolean pluginPresent, boolean pluginEnabled, String version, String reason) {
            return new MythicAvailability(pluginPresent, pluginEnabled, false, false, version, reason, null, null);
        }

        private boolean available() {
            return pluginPresent && pluginEnabled && apiAvailable && helperAvailable && helper != null;
        }
    }
}
