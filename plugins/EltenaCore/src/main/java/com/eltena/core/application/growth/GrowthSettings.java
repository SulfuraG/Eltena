package com.eltena.core.application.growth;

import com.eltena.core.domain.stats.GrowthType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;

public final class GrowthSettings {

    private static final String BALANCE_FILE_NAME = "growth-balance.yml";
    private static final String DEFAULT_EXP_CURVE_FILE_NAME = "Rebellious_Fallen_Angel_levels.txt";

    private final JavaPlugin plugin;
    private YamlConfiguration balanceConfig;
    private List<Long> expCurve = List.of();
    private boolean expCurveWarningLogged;

    public GrowthSettings(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        reload();
    }

    public void reload() {
        Path dataFolder = plugin.getDataFolder().toPath();
        ensureBundledBalanceFile(dataFolder.resolve(BALANCE_FILE_NAME), BALANCE_FILE_NAME);
        this.balanceConfig = loadConfiguration(dataFolder.resolve(BALANCE_FILE_NAME), BALANCE_FILE_NAME);
        ensureBundledPlainTextFile(dataFolder.resolve(expCurveFileName()), DEFAULT_EXP_CURVE_FILE_NAME);
        this.expCurve = loadExpCurve(dataFolder.resolve(expCurveFileName()));
        this.expCurveWarningLogged = false;
    }

    public String targetRange() {
        return balance().getString("balance.target-range", "lv1-30");
    }

    public double baseHpExact() {
        return Math.max(1.0D, balance().getDouble("progression.base-player-stats.hp", 20.0D));
    }

    public int baseHp() {
        return Math.max(1, (int) Math.round(baseHpExact()));
    }

    public int baseMp() {
        return Math.max(0, balance().getInt("progression.base-player-stats.mp", 10));
    }

    public double baseMaxMpExact() {
        return Math.max(1.0D, balance().getDouble("progression.base-player-stats.max-mp", 20.0D));
    }

    public int baseMaxMp() {
        return Math.max(1, (int) Math.round(baseMaxMpExact()));
    }

    public int baseAttack() {
        return Math.max(0, balance().getInt("progression.base-player-stats.attack", 5));
    }

    public int baseDefense() {
        return Math.max(0, balance().getInt("progression.base-player-stats.defense", 5));
    }

    public int baseStrength() {
        return 0;
    }

    public int baseDexterity() {
        return 0;
    }

    public int baseIntelligence() {
        return 0;
    }

    public int levelCap() {
        int configured = Math.max(1, balance().getInt("progression.level.cap", 1500));
        int available = expCurveMaxLevel();
        if (configured > available && !expCurveWarningLogged) {
            expCurveWarningLogged = true;
            plugin.getLogger().warning(
                "[EltenaCore] EXPカーブの行数が不足しています。"
                    + " 設定上限=" + configured
                    + " 利用可能上限=" + available
                    + " curve-file=" + expCurveFileName()
            );
        }
        return Math.max(1, Math.min(configured, available));
    }

    public boolean restoreMpOnLevelUp() {
        return balance().getBoolean(
            "progression.level.restore-mp-on-level-up",
            balance().getBoolean("level-up.restore-mp-on-level-up", true)
        );
    }

    public boolean levelUpSoundEnabled() {
        return balance().getBoolean("progression.level.sound.enabled", true);
    }

    public String levelUpSoundName() {
        return balance().getString("progression.level.sound.name", "ENTITY_PLAYER_LEVELUP");
    }

    public float levelUpSoundVolume() {
        return (float) Math.max(0.0D, balance().getDouble("progression.level.sound.volume", 1.0D));
    }

    public float levelUpSoundPitch() {
        return (float) Math.max(0.0D, balance().getDouble("progression.level.sound.pitch", 1.0D));
    }

    public long requiredExpForLevel(int level) {
        int normalizedLevel = Math.max(1, level);
        if (!expCurve.isEmpty()) {
            int index = Math.min(normalizedLevel - 1, expCurve.size() - 1);
            return Math.max(1L, expCurve.get(index));
        }
        return resolveLongFromNumericTable(
            "progression.level.required-exp-to-next",
            normalizedLevel,
            legacyRequiredExpForLevel(level)
        );
    }

    public LevelUpReward levelUpRewardFor(double currentHpExact, double currentMaxMpExact) {
        return new LevelUpReward(
            calculatePercentGrowth(currentHpExact, hpGrowthPercentPerLevel(), hpGrowthDecimals()),
            calculatePercentGrowth(currentMaxMpExact, mpGrowthPercentPerLevel(), mpGrowthDecimals())
        );
    }

    public double hpGrowthPercentPerLevel() {
        return Math.max(0.0D, balance().getDouble("progression.level.hp.percent-per-level", 3.0D));
    }

    public int hpGrowthDecimals() {
        return Math.max(0, balance().getInt("progression.level.hp.decimals", 2));
    }

    public double mpGrowthPercentPerLevel() {
        return Math.max(0.0D, balance().getDouble("progression.level.mp.percent-per-level", 3.0D));
    }

    public int mpGrowthDecimals() {
        return Math.max(0, balance().getInt("progression.level.mp.decimals", 2));
    }

    public String expCurveFileName() {
        String configured = balance().getString("progression.level.curve-file", DEFAULT_EXP_CURVE_FILE_NAME);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_EXP_CURVE_FILE_NAME;
        }
        return configured.trim();
    }

    public int expCurveLineCount() {
        return expCurve.size();
    }

    public long requiredExpForStatGrowth(int level) {
        return resolveLongFromNumericTable(
            "progression.stat-growth.required-exp-to-next",
            Math.max(0, level),
            legacyRequiredExpForStatGrowth(level)
        );
    }

    public StatGrowthReward statGrowthReward(GrowthType type) {
        String key = switch (type) {
            case ATTACK -> "attack";
            case MP -> "mp";
            case DEFENSE -> "defense";
        };
        ConfigurationSection section = balance().getConfigurationSection("progression.stat-growth.rewards." + key);
        if (section == null) {
            return StatGrowthReward.none();
        }
        return new StatGrowthReward(
            Math.max(0, section.getInt("attack-per-level", 0)),
            Math.max(0, section.getInt("mp-per-level", 0)),
            Math.max(0, section.getInt("max-mp-per-level", 0)),
            Math.max(0, section.getInt("defense-per-level", 0)),
            Math.max(0, section.getInt("bonus-every-levels", 0)),
            Math.max(0, section.getInt("bonus-attack", 0)),
            Math.max(0, section.getInt("bonus-defense", 0)),
            Math.max(0, section.getInt("bonus-max-mp", 0))
        );
    }

    public long requiredExpForWeaponMastery(int level) {
        return resolveLongFromNumericTable(
            "progression.weapon-mastery.required-exp-to-next",
            Math.max(0, level),
            legacyRequiredExpForWeaponMastery(level)
        );
    }

    public WeaponMasteryRewardModel weaponMasteryRewardModel() {
        ConfigurationSection section = balance().getConfigurationSection("progression.weapon-mastery.reward-model");
        if (section == null) {
            return new WeaponMasteryRewardModel(0.0D, 0.0D, Map.of());
        }
        return new WeaponMasteryRewardModel(
            Math.max(0.0D, section.getDouble("damage-percent-per-level", 0.0D)),
            Math.max(0.0D, section.getDouble("skill-efficiency-percent-per-level", 0.0D)),
            loadStringTable("progression.weapon-mastery.reward-model.milestone-levels")
        );
    }

    public Optional<String> weaponMasteryMilestoneFor(int level) {
        return Optional.ofNullable(weaponMasteryRewardModel().milestoneLevels().get(Math.max(0, level)));
    }

    public StrengthRoleSettings strengthRoleSettings() {
        ConfigurationSection section = balance().getConfigurationSection("attributes.roles.strength");
        if (section == null) {
            return new StrengthRoleSettings(0.0D, 0);
        }
        return new StrengthRoleSettings(
            Math.max(0.0D, section.getDouble("weapon-damage-multiplier-per-point", 0.0D)),
            Math.max(0, section.getInt("hp-bonus-per-5-points", 0))
        );
    }

    public DexterityRoleSettings dexterityRoleSettings() {
        ConfigurationSection section = balance().getConfigurationSection("attributes.roles.dexterity");
        if (section == null) {
            return new DexterityRoleSettings(0.0D, 0.0D, 0.0D);
        }
        return new DexterityRoleSettings(
            Math.max(0.0D, section.getDouble("crit-rate-per-10-points", 0.0D)),
            Math.max(0.0D, section.getDouble("accuracy-per-10-points", 0.0D)),
            Math.max(0.0D, section.getDouble("mastery-gain-multiplier-per-10-points", 0.0D))
        );
    }

    public IntelligenceRoleSettings intelligenceRoleSettings() {
        ConfigurationSection section = balance().getConfigurationSection("attributes.roles.intelligence");
        if (section == null) {
            return new IntelligenceRoleSettings(0, 0.0D, 0.30D);
        }
        return new IntelligenceRoleSettings(
            Math.max(0, section.getInt("max-mp-bonus-per-point", 0)),
            Math.max(0.0D, section.getDouble("cooldown-reduction-per-10-points", 0.0D)),
            Math.max(0.0D, section.getDouble("max-cooldown-reduction", 0.30D))
        );
    }

    public PowerShareSettings earlyGamePowerShare() {
        return loadPowerShare("equipment.power-share.early-game");
    }

    public PowerShareSettings midGamePowerShare() {
        return loadPowerShare("equipment.power-share.mid-game");
    }

    public boolean debugEventsEnabled() {
        return config().getBoolean("growth.debug-events", false);
    }

    public boolean combatEnabled() {
        return false;
    }

    public int combatCooldownTicks() {
        return Math.max(1, config().getInt("growth.combat.cooldown-ticks", 10));
    }

    public long normalMobAttackExp() {
        return Math.max(1, config().getLong("growth.combat.normal-mob-attack-exp", 5L));
    }

    public long strongMobAttackExp() {
        return Math.max(1, config().getLong("growth.combat.strong-mob-attack-exp", 15L));
    }

    public long defaultMobExp() {
        return Math.max(1, config().getLong("progression.level.default-mob-exp", 10L));
    }

    public long unknownMobAttackExp() {
        return Math.max(1, config().getLong("growth.combat.unknown-mob-attack-exp", 3L));
    }

    public double strongMobHealthThreshold() {
        return Math.max(1.0D, config().getDouble("growth.combat.strong-mob-health-threshold", 40.0D));
    }

    public boolean combatDebugEnabled() {
        return config().getBoolean("combat.debug", false);
    }

    public Map<String, Integer> abilityUnlockByLevel() {
        ConfigurationSection section = balance().getConfigurationSection("abilities.unlock-by-level");
        if (section == null) {
            return Map.of();
        }
        LinkedHashMap<String, Integer> levels = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            String abilityId = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
            if (abilityId.isBlank()) {
                continue;
            }
            int level = Math.max(1, section.getInt(key, 1));
            levels.put(abilityId, level);
        }
        return Map.copyOf(levels);
    }

    public boolean defenseEnabled() {
        return false;
    }

    public long normalDamageDefenseExp() {
        return Math.max(1, config().getLong("growth.defense.normal-damage-defense-exp", 2L));
    }

    public long mobDamageDefenseExp() {
        return Math.max(1, config().getLong("growth.defense.mob-damage-defense-exp", 4L));
    }

    public long shieldDefenseExp() {
        return Math.max(1, config().getLong("growth.defense.shield-defense-exp", 6L));
    }

    public int defenseCooldownTicks() {
        return Math.max(1, config().getInt("growth.defense.cooldown-ticks", 40));
    }

    public boolean masteryEnabled() {
        return false;
    }

    public long masteryAttackExp() {
        return Math.max(1, config().getLong("growth.mastery.attack-exp", 3L));
    }

    public long masteryKillBonusExp() {
        return Math.max(0, config().getLong("growth.mastery.kill-bonus-exp", 5L));
    }

    public int masteryCooldownTicks() {
        return Math.max(1, config().getInt("growth.mastery.cooldown-ticks", 20));
    }

    public boolean suppressMobExpOrbs() {
        return config().getBoolean("server-rules.mob-exp-orbs.enabled", true);
    }

    public boolean suppressExperienceOrbSpawns() {
        return config().getBoolean("server-rules.mob-exp-orbs.suppress-orb-spawns", true);
    }

    public boolean hungerLockEnabled() {
        return config().getBoolean("server-rules.hunger-lock.enabled", true);
    }

    public boolean refillFoodOnJoin() {
        return config().getBoolean("server-rules.hunger-lock.refill-on-join", true);
    }

    public boolean refillFoodOnRespawn() {
        return config().getBoolean("server-rules.hunger-lock.refill-on-respawn", true);
    }

    public int fixedFoodLevel() {
        return Math.max(1, Math.min(20, config().getInt("server-rules.hunger-lock.food-level", 20)));
    }

    public float fixedSaturation() {
        return (float) Math.max(0.0D, config().getDouble("server-rules.hunger-lock.saturation", 20.0D));
    }

    public boolean blockNaturalMobSpawns() {
        return config().getBoolean("server-rules.natural-spawns.enabled", true);
    }

    public boolean killExpEnabled() {
        return config().getBoolean("exp.kill.enabled", true);
    }

    public long killExpDefault() {
        return Math.max(0L, config().getLong("exp.kill.default", defaultMobExp()));
    }

    public long killExpVanillaDefault() {
        return Math.max(0L, config().getLong("exp.kill.vanilla.default", killExpDefault()));
    }

    public Map<EntityType, Long> killExpByEntityType() {
        Map<EntityType, Long> values = new LinkedHashMap<>();
        loadEntityTypeValues(values, "exp.kill.by-entity-type");
        loadEntityTypeValues(values, "exp.kill.vanilla.by-entity-type");
        return values;
    }

    public Map<String, Long> killExpByName() {
        Map<String, Long> values = new LinkedHashMap<>();
        if (config().getConfigurationSection("exp.kill.by-name") == null) {
            return values;
        }
        for (String rawKey : config().getConfigurationSection("exp.kill.by-name").getKeys(false)) {
            values.put(normalizeDisplayName(rawKey), Math.max(0L, config().getLong("exp.kill.by-name." + rawKey, 0L)));
        }
        return values;
    }

    public boolean mythicKillExpEnabled() {
        return config().getBoolean("exp.kill.mythic-mobs.enabled", true);
    }

    public long mythicKillExpDefault() {
        return Math.max(0L, config().getLong("exp.kill.mythic-mobs.default", killExpDefault()));
    }

    public long mythicKillExpFallback() {
        return Math.max(0L, config().getLong("exp.kill.mythic-mobs.fallback", mythicKillExpDefault()));
    }

    public boolean mythicKillExpUseOptionsExperience() {
        return config().getBoolean("exp.kill.mythic-mobs.use-options-experience", true);
    }

    public boolean mythicKillExpZeroExperienceMeansZero() {
        return config().getBoolean("exp.kill.mythic-mobs.zero-experience-means-zero", true);
    }

    public Map<String, Long> mythicKillExpByInternalName() {
        Map<String, Long> values = new LinkedHashMap<>();
        if (config().getConfigurationSection("exp.kill.mythic-mobs.by-internal-name") == null) {
            return values;
        }
        for (String rawKey : config().getConfigurationSection("exp.kill.mythic-mobs.by-internal-name").getKeys(false)) {
            values.put(normalizeMobId(rawKey), Math.max(0L, config().getLong("exp.kill.mythic-mobs.by-internal-name." + rawKey, 0L)));
        }
        return values;
    }

    public boolean isStrongMob(LivingEntity entity) {
        if (entity instanceof Monster) {
            return entity.getMaxHealth() >= strongMobHealthThreshold();
        }
        return entity.getMaxHealth() >= strongMobHealthThreshold() * 2.0D;
    }

    public boolean isHostileMob(LivingEntity entity) {
        return entity instanceof Monster;
    }

    public boolean isOnCooldown(Map<UUID, Integer> cooldowns, UUID playerId, int currentTick, int cooldownTicks) {
        Integer lastTick = cooldowns.get(playerId);
        return lastTick != null && currentTick - lastTick < cooldownTicks;
    }

    public void markTriggered(Map<UUID, Integer> cooldowns, UUID playerId, int currentTick) {
        cooldowns.put(playerId, currentTick);
    }

    public void debugLog(String message) {
        if (debugEventsEnabled()) {
            plugin.getLogger().info("[GrowthDebug] " + message);
        }
    }

    public void combatDebugLog(String message) {
        if (combatDebugEnabled()) {
            plugin.getLogger().info("[EltenaCore] " + message);
        }
    }

    public String normalizeDisplayName(String rawName) {
        if (rawName == null) {
            return "";
        }
        return rawName.replace("§", "&").trim();
    }

    public String normalizeMobId(String rawName) {
        if (rawName == null) {
            return "";
        }
        return rawName.trim().toLowerCase(Locale.ROOT);
    }

    private long legacyRequiredExpForLevel(int level) {
        int normalizedLevel = Math.max(1, level);
        return Math.max(1L, config().getLong("progression.level.base-required-exp", 100L))
            + (long) Math.max(0, normalizedLevel - 1) * Math.max(0L, config().getLong("progression.level.required-exp-per-level", 25L));
    }

    private int expCurveMaxLevel() {
        if (expCurve == null || expCurve.isEmpty()) {
            return Math.max(1, balance().getInt("progression.level.cap", 1500));
        }
        return expCurve.size() + 1;
    }

    private double calculatePercentGrowth(double currentValue, double percentPerLevel, int decimals) {
        if (currentValue <= 0.0D || percentPerLevel <= 0.0D) {
            return 0.0D;
        }
        return roundExact(currentValue * (percentPerLevel / 100.0D), decimals);
    }

    private double roundExact(double value, int decimals) {
        return com.eltena.core.domain.stats.PlayerStats.roundExact(value, decimals);
    }

    private long legacyRequiredExpForStatGrowth(int level) {
        int normalizedLevel = Math.max(0, level);
        return Math.max(1L, config().getLong("progression.stat-growth.base-required-exp", 50L))
            + (long) normalizedLevel * Math.max(0L, config().getLong("progression.stat-growth.required-exp-per-level", 10L));
    }

    private long legacyRequiredExpForWeaponMastery(int level) {
        int normalizedLevel = Math.max(0, level);
        return Math.max(1L, config().getLong("progression.weapon-mastery.base-required-exp", 40L))
            + (long) normalizedLevel * Math.max(0L, config().getLong("progression.weapon-mastery.required-exp-per-level", 8L));
    }

    private long resolveLongFromNumericTable(String path, int key, long fallback) {
        NavigableMap<Integer, Long> values = loadLongTable(path);
        if (values.isEmpty()) {
            return Math.max(1L, fallback);
        }
        Long exact = values.get(key);
        if (exact != null) {
            return Math.max(1L, exact);
        }
        Map.Entry<Integer, Long> floor = values.floorEntry(key);
        if (floor != null) {
            return Math.max(1L, floor.getValue());
        }
        return Math.max(1L, values.lastEntry().getValue());
    }

    private NavigableMap<Integer, Long> loadLongTable(String path) {
        NavigableMap<Integer, Long> values = new TreeMap<>();
        ConfigurationSection section = balance().getConfigurationSection(path);
        if (section == null) {
            return values;
        }
        for (String rawKey : section.getKeys(false)) {
            try {
                int key = Integer.parseInt(rawKey.trim());
                values.put(key, Math.max(0L, section.getLong(rawKey, 0L)));
            } catch (NumberFormatException ignored) {
            }
        }
        return values;
    }

    private Map<Integer, String> loadStringTable(String path) {
        Map<Integer, String> values = new LinkedHashMap<>();
        ConfigurationSection section = balance().getConfigurationSection(path);
        if (section == null) {
            return values;
        }
        for (String rawKey : section.getKeys(false)) {
            try {
                int key = Integer.parseInt(rawKey.trim());
                String value = section.getString(rawKey, "");
                if (!value.isBlank()) {
                    values.put(key, value);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return values;
    }

    private PowerShareSettings loadPowerShare(String path) {
        ConfigurationSection section = balance().getConfigurationSection(path);
        if (section == null) {
            return new PowerShareSettings(0, 0);
        }
        return new PowerShareSettings(
            Math.max(0, section.getInt("base-and-level-percent", 0)),
            Math.max(0, section.getInt("equipment-percent", 0))
        );
    }

    private void loadEntityTypeValues(Map<EntityType, Long> values, String path) {
        if (config().getConfigurationSection(path) == null) {
            return;
        }
        for (String rawKey : config().getConfigurationSection(path).getKeys(false)) {
            try {
                EntityType type = EntityType.valueOf(rawKey.trim().toUpperCase(Locale.ROOT));
                values.put(type, Math.max(0L, config().getLong(path + "." + rawKey, 0L)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void ensureBundledBalanceFile(Path file, String resourcePath) {
        if (Files.notExists(file) && plugin.getResource(resourcePath) != null) {
            plugin.saveResource(resourcePath, false);
        }
        syncBundledBalanceFile(file, resourcePath);
    }

    private void ensureBundledPlainTextFile(Path file, String resourcePath) {
        if (Files.exists(file)) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create exp curve directory", exception);
            return;
        }
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream == null) {
                return;
            }
            Files.copy(stream, file);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save exp curve file", exception);
        }
    }

    private List<Long> loadExpCurve(Path file) {
        List<Long> values = new ArrayList<>();
        if (Files.notExists(file)) {
            plugin.getLogger().warning("[EltenaCore] EXPカーブファイルが見つかりません: " + file.getFileName());
            return values;
        }
        try {
            int lineNumber = 0;
            for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                lineNumber++;
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                try {
                    long value = Long.parseLong(line);
                    if (value <= 0L) {
                        plugin.getLogger().warning(
                            "[EltenaCore] EXPカーブの値が0以下です。"
                                + " file=" + file.getFileName()
                                + " line=" + lineNumber
                                + " value=" + line
                        );
                        continue;
                    }
                    values.add(value);
                } catch (NumberFormatException exception) {
                    plugin.getLogger().warning(
                        "[EltenaCore] EXPカーブの読み込みに失敗しました。"
                            + " file=" + file.getFileName()
                            + " line=" + lineNumber
                            + " value=" + line
                    );
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load exp curve file " + file.getFileName(), exception);
        }
        if (values.isEmpty()) {
            plugin.getLogger().warning("[EltenaCore] EXPカーブが空のため、旧設定を使用します。");
        } else if (values.size() < 1499) {
            plugin.getLogger().warning(
                "[EltenaCore] EXPカーブの行数が不足しています。"
                    + " 現在行数=" + values.size()
                    + " 必要行数=1499"
                    + " file=" + file.getFileName()
            );
        } else {
            plugin.getLogger().info(
                "[EltenaCore] EXPカーブを読み込みました。"
                    + " file=" + file.getFileName()
                    + " lines=" + values.size()
            );
        }
        return List.copyOf(values);
    }

    private void syncBundledBalanceFile(Path file, String resourcePath) {
        if (Files.notExists(file)) {
            return;
        }
        YamlConfiguration bundled = loadBundledConfiguration(resourcePath);
        if (bundled.getKeys(true).isEmpty()) {
            return;
        }
        YamlConfiguration existing = new YamlConfiguration();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            existing.load(reader);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load growth balance file for sync", exception);
            return;
        }

        boolean changed = false;
        for (String key : bundled.getKeys(true)) {
            if (bundled.isConfigurationSection(key) || existing.contains(key)) {
                continue;
            }
            existing.set(key, bundled.get(key));
            changed = true;
        }

        if (!changed) {
            return;
        }

        try {
            Files.writeString(file, existing.saveToString(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to write synced growth balance file", exception);
        }
    }

    private YamlConfiguration loadConfiguration(Path file, String resourcePath) {
        YamlConfiguration defaults = loadBundledConfiguration(resourcePath);
        YamlConfiguration configuration = new YamlConfiguration();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                configuration.load(reader);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load " + file.getFileName(), exception);
                configuration = new YamlConfiguration();
            }
        }
        configuration.setDefaults(defaults);
        return configuration;
    }

    private YamlConfiguration loadBundledConfiguration(String resourcePath) {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream == null) {
                return defaults;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                defaults.load(reader);
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load bundled " + resourcePath, exception);
        }
        return defaults;
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    private YamlConfiguration balance() {
        if (balanceConfig == null) {
            reload();
        }
        return balanceConfig;
    }

    public record LevelUpReward(
        double hp,
        double maxMp
    ) {
        public static LevelUpReward none() {
            return new LevelUpReward(0.0D, 0.0D);
        }

        public LevelUpReward add(LevelUpReward other) {
            return new LevelUpReward(
                hp + other.hp,
                maxMp + other.maxMp
            );
        }

        public boolean hasAnyValue() {
            return hp > 0
                || maxMp > 0;
        }
    }

    public record StatGrowthReward(
        int attackPerLevel,
        int mpPerLevel,
        int maxMpPerLevel,
        int defensePerLevel,
        int bonusEveryLevels,
        int bonusAttack,
        int bonusDefense,
        int bonusMaxMp
    ) {
        public static StatGrowthReward none() {
            return new StatGrowthReward(0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    public record WeaponMasteryRewardModel(
        double damagePercentPerLevel,
        double skillEfficiencyPercentPerLevel,
        Map<Integer, String> milestoneLevels
    ) {
    }

    public record StrengthRoleSettings(
        double weaponDamageMultiplierPerPoint,
        int hpBonusPerFivePoints
    ) {
    }

    public record DexterityRoleSettings(
        double critRatePerTenPoints,
        double accuracyPerTenPoints,
        double masteryGainMultiplierPerTenPoints
    ) {
    }

    public record IntelligenceRoleSettings(
        int maxMpBonusPerPoint,
        double cooldownReductionPerTenPoints,
        double maxCooldownReduction
    ) {
    }

    public record PowerShareSettings(
        int baseAndLevelPercent,
        int equipmentPercent
    ) {
    }
}
