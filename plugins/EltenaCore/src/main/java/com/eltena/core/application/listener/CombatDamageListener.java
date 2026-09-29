package com.eltena.core.application.listener;

import com.eltena.core.application.equipment.MmoItemsDefinitionBonuses;
import com.eltena.core.application.equipment.MmoItemsEquipmentBonuses;
import com.eltena.core.application.equipment.WeaponAttackSnapshotResolver;
import com.eltena.core.application.growth.GrowthSettings;
import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.player.PlayerProfile;
import com.eltena.core.domain.stats.PlayerStats;
import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class CombatDamageListener implements Listener {

    private final ServiceRegistry services;
    private final GrowthSettings settings;

    public CombatDamageListener(ServiceRegistry services, GrowthSettings settings) {
        this.services = services;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        CombatContext context = resolveCombatContext(event);
        if (context == null) {
            return;
        }
        long appliedDamage = Math.max(0L, Math.round(event.getFinalDamage()));
        showFinalDamage(context.attacker(), context.target(), appliedDamage);
        showChatDamage(context.attacker(), context.target(), appliedDamage);
        services.playerDamageFeedbackService().recordHit(context.attacker(), appliedDamage);
        logObservedDamage(context, event, appliedDamage);
    }

    private void logObservedDamage(CombatContext context, EntityDamageByEntityEvent event, long appliedDamage) {
        if (!settings.combatDebugEnabled()) {
            return;
        }
        Player attacker = context.attacker();
        LivingEntity target = context.target();
        PlayerProfile profile = loadProfile(attacker);
        PlayerStats playerStats = loadStats(attacker);
        MmoItemsEquipmentBonuses mmoItemsEquipmentBonuses = services.mmoItemsEquipmentStatsProvider().resolve(profile);
        MmoItemsDefinitionBonuses mmoItemsBonuses = services.mmoItemsDefinitionStatsProvider().resolve(profile);
        int baseAttack = playerStats == null ? 0 : playerStats.attack();
        WeaponAttackSnapshotResolver.WeaponAttackSnapshot attackSnapshot =
            services.weaponAttackSnapshotResolver().resolveForCombat(attacker, baseAttack, event.getDamage());
        Set<String> excludedItemIds = new LinkedHashSet<>();
        if (!mmoItemsEquipmentBonuses.resolvedItemIds().isEmpty()) {
            excludedItemIds.addAll(mmoItemsEquipmentBonuses.resolvedItemIds());
        } else {
            excludedItemIds.addAll(mmoItemsBonuses.mappedItemIds());
        }

        for (var entry : services.equipmentSystem().debugEntries(profile, excludedItemIds)) {
            settings.combatDebugLog(
                "陬・ｙ遒ｺ隱・ slot=" + entry.slot()
                    + " item=" + entry.itemId()
                    + " matched=" + entry.matchedDefinitionId()
            );
        }

        double targetDefense = resolveTargetDefense(target);
        double attackPower = resolveAttackPower(attackSnapshot);
        settings.combatDebugLog(
            "謌ｦ髣倩ｨ育ｮ医し繝ｼ繝舌・隕九ｮ・ attacker=" + attacker.getName()
                + " target=" + target.getType()
                + " baseAttack=" + attackSnapshot.baseAttack()
                + " weaponAttack=" + attackSnapshot.weaponAttack()
                + " attackPower(displayOnly)=" + attackPower
                + " targetDefense(readOnly)=" + targetDefense
                + " mmoItemsEquipment=" + mmoItemsEquipmentBonuses.summary()
                + " mmoItemsBonus=" + mmoItemsBonuses.summary()
                + " equipmentBonus=" + services.equipmentSystem().debugSummary(profile, excludedItemIds)
                + " eventDamage=" + event.getDamage()
                + " eventFinalDamage=" + event.getFinalDamage()
                + " displayedFinalDamage=" + appliedDamage
        );
    }

    private PlayerProfile loadProfile(Player attacker) {
        try {
            return services.playerProfiles().loadOrCreate(attacker.getUniqueId(), attacker.getName());
        } catch (IOException exception) {
            services.plugin().getLogger().warning(
                "[EltenaCore] Failed to load profile for " + attacker.getName() + ": " + exception.getMessage()
            );
            return null;
        }
    }

    private PlayerStats loadStats(Player attacker) {
        try {
            return services.playerStats().loadOrCreate(attacker.getUniqueId());
        } catch (IOException exception) {
            services.plugin().getLogger().warning(
                "[EltenaCore] Failed to load player stats for " + attacker.getName() + ": " + exception.getMessage()
            );
            return null;
        }
    }

    private double resolveTargetDefense(LivingEntity target) {
        if (target == null || !target.isValid()) {
            return 0.0D;
        }
        for (String candidate : new String[]{"ARMOR", "GENERIC_ARMOR"}) {
            try {
                Attribute attribute = Attribute.valueOf(candidate);
                var instance = target.getAttribute(attribute);
                if (instance != null) {
                    return Math.max(0.0D, instance.getValue());
                }
            } catch (IllegalArgumentException ignored) {
                // Runtime attribute name differs between platforms.
            }
        }
        return 0.0D;
    }

    private double resolveAttackPower(WeaponAttackSnapshotResolver.WeaponAttackSnapshot snapshot) {
        if (snapshot == null) {
            return 0.0D;
        }
        return Math.max(0.0D, snapshot.baseAttack() + snapshot.weaponAttack());
    }

    private CombatContext resolveCombatContext(EntityDamageByEntityEvent event) {
        if (event == null) {
            return null;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            return null;
        }
        if (!(event.getEntity() instanceof LivingEntity target) || target instanceof Player) {
            return null;
        }
        return new CombatContext(attacker, target);
    }

    private void showFinalDamage(Player attacker, LivingEntity target, long finalDamage) {
        if (attacker == null || isDummyTarget(target) || finalDamage <= 0L || !target.isValid()) {
            return;
        }
        if (!services.playerOptionSettingsService().isFloatingDamageEnabled(attacker.getUniqueId())) {
            return;
        }
        HorizontalOffset horizontalOffset = randomHorizontalOffset(target);
        Location base = target.getLocation().clone().add(
            horizontalOffset.x(),
            randomVerticalOffset(target),
            horizontalOffset.z()
        );
        TextDisplay display = target.getWorld().spawn(base, TextDisplay.class, entity -> {
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setSeeThrough(true);
            entity.setShadowed(false);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            entity.setTextOpacity((byte) 127);
            entity.setTransformation(new Transformation(
                new Vector3f(),
                new org.joml.Quaternionf(),
                new Vector3f(1.2F, 1.2F, 1.2F),
                new org.joml.Quaternionf()
            ));
            entity.setText(colorize(services.messages().get(
                "combat.floating-damage.format",
                "color", floatingDamageColor(),
                "damage", formatNumber(finalDamage)
            )));
        });
        services.plugin().getServer().getScheduler().runTaskLater(services.plugin(), display::remove, 15L);
    }

    private void showChatDamage(Player attacker, LivingEntity target, long finalDamage) {
        if (attacker == null || finalDamage <= 0L || target == null) {
            return;
        }
        if (!services.playerOptionSettingsService().isChatDamageEnabled(attacker.getUniqueId())) {
            return;
        }
        String mobName = resolveMobName(target);
        String level = resolveMobLevel(target);
        long maxHealth = Math.max(0L, Math.round(resolveDisplayedMaxHealth(target)));
        services.plugin().getServer().getScheduler().runTask(services.plugin(), () -> {
            if (!attacker.isOnline()) {
                return;
            }
            long currentHealth = Math.max(0L, Math.round(resolveDisplayedCurrentHealth(target)));
            if (maxHealth > 0L && currentHealth > maxHealth) {
                currentHealth = maxHealth;
            }
            attacker.sendMessage(colorize(services.messages().get(
                "combat.chat-damage.format",
                "level", level,
                "mob", mobName,
                "current_hp", formatNumber(currentHealth),
                "max_hp", formatNumber(maxHealth),
                "damage", formatNumber(finalDamage)
            )));
        });
    }

    private String resolveMobName(LivingEntity target) {
        String resolved = services.mobKillExperienceService().resolveDisplayTargetName(target);
        if (resolved != null && !resolved.isBlank()) {
            return resolved;
        }
        return target == null ? "" : target.getName();
    }

    private String resolveMobLevel(LivingEntity target) {
        ActiveMob activeMob = resolveActiveMob(target);
        if (activeMob == null) {
            return services.messages().get("combat.chat-damage.unknown-level");
        }
        double level = activeMob.getLevel();
        if (!Double.isFinite(level)) {
            return services.messages().get("combat.chat-damage.unknown-level");
        }
        return formatNumber(Math.max(0L, Math.round(level)));
    }

    private ActiveMob resolveActiveMob(LivingEntity target) {
        if (target == null) {
            return null;
        }
        try {
            MythicBukkit mythic = MythicBukkit.inst();
            if (mythic == null) {
                return null;
            }
            BukkitAPIHelper helper = mythic.getAPIHelper();
            return helper == null ? null : helper.getMythicMobInstance(target);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private double resolveDisplayedCurrentHealth(LivingEntity target) {
        if (target == null || target.isDead() || !target.isValid()) {
            return 0.0D;
        }
        return Math.max(0.0D, target.getHealth());
    }

    private double resolveDisplayedMaxHealth(LivingEntity target) {
        if (target == null) {
            return 0.0D;
        }
        Attribute attribute = resolveAttribute("MAX_HEALTH", "GENERIC_MAX_HEALTH");
        if (attribute == null) {
            return Math.max(0.0D, target.getHealth());
        }
        var instance = target.getAttribute(attribute);
        if (instance == null) {
            return Math.max(0.0D, target.getHealth());
        }
        return Math.max(0.0D, instance.getValue());
    }

    private Attribute resolveAttribute(String... candidates) {
        for (String candidate : candidates) {
            try {
                return Attribute.valueOf(candidate);
            } catch (IllegalArgumentException ignored) {
                // Runtime attribute name differs between platforms.
            }
        }
        return null;
    }

    private String floatingDamageColor() {
        return services.messages().get("combat.floating-damage.color");
    }

    private double randomOffset(String minKey, String maxKey, double fallbackMin, double fallbackMax) {
        double configuredMin = services.messages().getDouble(minKey, fallbackMin);
        double configuredMax = services.messages().getDouble(maxKey, fallbackMax);
        return randomBetween(configuredMin, configuredMax);
    }

    private HorizontalOffset randomHorizontalOffset(LivingEntity target) {
        double radiusMultiplierMin = services.messages().getDouble(
            "combat.floating-damage.random-offset.radius-multiplier-min",
            Double.NaN
        );
        double radiusMultiplierMax = services.messages().getDouble(
            "combat.floating-damage.random-offset.radius-multiplier-max",
            Double.NaN
        );
        double radiusExtraMin = services.messages().getDouble(
            "combat.floating-damage.random-offset.radius-extra-min",
            Double.NaN
        );
        double radiusExtraMax = services.messages().getDouble(
            "combat.floating-damage.random-offset.radius-extra-max",
            Double.NaN
        );
        if (Double.isFinite(radiusMultiplierMin)
            && Double.isFinite(radiusMultiplierMax)
            && Double.isFinite(radiusExtraMin)
            && Double.isFinite(radiusExtraMax)) {
            double width = target == null ? 0.0D : Math.max(0.1D, target.getWidth());
            double radiusMultiplier = randomBetween(radiusMultiplierMin, radiusMultiplierMax);
            double radiusExtra = randomBetween(radiusExtraMin, radiusExtraMax);
            double radius = Math.max(0.05D, width * radiusMultiplier + radiusExtra);
            double angle = ThreadLocalRandom.current().nextDouble(0.0D, Math.PI * 2.0D);
            return new HorizontalOffset(Math.cos(angle) * radius, Math.sin(angle) * radius);
        }

        return new HorizontalOffset(
            randomOffset("combat.floating-damage.random-offset.x-min", "combat.floating-damage.random-offset.x-max", -0.45D, 0.45D),
            randomOffset("combat.floating-damage.random-offset.z-min", "combat.floating-damage.random-offset.z-max", -0.45D, 0.45D)
        );
    }

    private double randomVerticalOffset(LivingEntity target) {
        double height = target == null ? 0.0D : Math.max(0.1D, target.getHeight());
        double ratioMin = services.messages().getDouble("combat.floating-damage.random-offset.y-ratio-min", Double.NaN);
        double ratioMax = services.messages().getDouble("combat.floating-damage.random-offset.y-ratio-max", Double.NaN);
        if (Double.isFinite(ratioMin) && Double.isFinite(ratioMax)) {
            return randomBetween(height * ratioMin, height * ratioMax);
        }

        double legacyMin = services.messages().getDouble("combat.floating-damage.random-offset.y-min", height * 0.65D);
        double legacyMax = services.messages().getDouble("combat.floating-damage.random-offset.y-max", height * 1.05D);
        return randomBetween(legacyMin, legacyMax);
    }

    private double randomBetween(double first, double second) {
        double min = Math.min(first, second);
        double max = Math.max(first, second);
        if (Double.compare(min, max) == 0) {
            return min;
        }
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    private String formatNumber(long value) {
        return formatNumber((double) value);
    }

    private String formatNumber(double value) {
        String numberFormat = services.messages().get("combat.chat-damage.number-format");
        if (!"integer".equalsIgnoreCase(numberFormat)) {
            return Double.toString(value);
        }
        return Long.toString(Math.max(0L, Math.round(value)));
    }

    private String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    private boolean isDummyTarget(LivingEntity target) {
        return target.getType().name().contains("DUMMMMMMY");
    }

    private record CombatContext(
        Player attacker,
        LivingEntity target
    ) {
    }

    private record HorizontalOffset(
        double x,
        double z
    ) {
    }
}
