package com.eltena.core.application.equipment;

import com.eltena.core.bootstrap.ServiceRegistry;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public final class WeaponAttackSnapshotResolver {

    private static final String[] ATTACK_DAMAGE_ATTRIBUTE_NAMES = {
        "ATTACK_DAMAGE",
        "GENERIC_ATTACK_DAMAGE"
    };

    private static final Map<Material, Double> VANILLA_WEAPON_ATTACK_FALLBACKS = Map.of(
        Material.WOODEN_SWORD, 4.0D,
        Material.GOLDEN_SWORD, 4.0D,
        Material.STONE_SWORD, 5.0D,
        Material.IRON_SWORD, 6.0D,
        Material.DIAMOND_SWORD, 7.0D,
        Material.NETHERITE_SWORD, 8.0D
    );

    private final ServiceRegistry services;
    private Attribute attackDamageAttribute;
    private boolean attackDamageAttributeResolved;

    public WeaponAttackSnapshotResolver(ServiceRegistry services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    public WeaponAttackSnapshot resolvePreview(Player player, int baseAttack) {
        return resolve(player, baseAttack, 0.0D, false);
    }

    public WeaponAttackSnapshot resolveForCombat(Player player, int baseAttack, double eventDamage) {
        return resolve(player, baseAttack, eventDamage, true);
    }

    private WeaponAttackSnapshot resolve(Player player, int baseAttack, double eventDamage, boolean hasEventDamage) {
        if (player == null || !player.isOnline()) {
            return new WeaponAttackSnapshot(baseAttack, 0.0D, fallbackCombatBaseDamage(baseAttack, 0.0D));
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand == null || mainHand.getType().isAir()) {
            return new WeaponAttackSnapshot(baseAttack, 0.0D, fallbackCombatBaseDamage(baseAttack, 0.0D));
        }

        double mmoItemsWeaponAttack = services.mmoItemsEquipmentStatsProvider().resolve(mainHand).weaponDamage();
        double attributeWeaponAttack = resolveAttributeWeaponAttack(mainHand);
        double fallbackWeaponAttack = resolveVanillaWeaponAttack(mainHand);
        AttackSelection selection = chooseWeaponAttack(mmoItemsWeaponAttack, attributeWeaponAttack, fallbackWeaponAttack);
        double weaponAttack = selection.value();
        double combatBaseDamage = resolveCombatBaseDamage(baseAttack, weaponAttack);
        if (services.growthSettings().combatDebugEnabled()) {
            services.growthSettings().combatDebugLog(
                "武器攻撃解決: mode=" + (hasEventDamage ? "combat" : "preview")
                    + " player=" + player.getName()
                    + " mainhand=" + mainHand.getType().getKey()
                    + " materialId=" + services.mmoItemsEquipmentStatsProvider().resolveMaterialId(mainHand)
                    + " itemType=" + safeValue(services.mmoItemsEquipmentStatsProvider().resolveItemType(mainHand))
                    + " itemId=" + safeValue(services.mmoItemsEquipmentStatsProvider().resolveItemId(mainHand))
                    + " mmoitemsAttack=" + mmoItemsWeaponAttack
                    + " attributeAttack=" + attributeWeaponAttack
                    + " vanillaFallback=" + fallbackWeaponAttack
                    + " result=" + weaponAttack
                    + " source=" + selection.source()
            );
        }
        return new WeaponAttackSnapshot(baseAttack, weaponAttack, combatBaseDamage);
    }

    private AttackSelection chooseWeaponAttack(double mmoItemsWeaponAttack, double attributeWeaponAttack, double fallbackWeaponAttack) {
        if (mmoItemsWeaponAttack > 0.0D) {
            return new AttackSelection(mmoItemsWeaponAttack, "MMOITEMS_ATTACK_DAMAGE");
        }
        if (attributeWeaponAttack > 0.0D) {
            return new AttackSelection(attributeWeaponAttack, "ATTRIBUTE_MODIFIER");
        }
        if (fallbackWeaponAttack > 0.0D) {
            return new AttackSelection(Math.max(0.0D, fallbackWeaponAttack), "VANILLA_FALLBACK");
        }
        return new AttackSelection(0.0D, "NONE");
    }

    private double resolveCombatBaseDamage(int baseAttack, double weaponAttack) {
        if (baseAttack > 0 || weaponAttack > 0.0D) {
            return Math.max(0.0D, baseAttack + weaponAttack);
        }
        return fallbackCombatBaseDamage(baseAttack, weaponAttack);
    }

    private double fallbackCombatBaseDamage(int baseAttack, double weaponAttack) {
        if (baseAttack > 0) {
            return baseAttack;
        }
        if (weaponAttack > 0.0D) {
            return Math.max(0.0D, baseAttack + weaponAttack);
        }
        return 1.0D;
    }

    private double resolveVanillaWeaponAttack(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0.0D;
        }
        return VANILLA_WEAPON_ATTACK_FALLBACKS.getOrDefault(stack.getType(), 0.0D);
    }

    private double resolveAttributeWeaponAttack(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0.0D;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasAttributeModifiers()) {
            return 0.0D;
        }
        Attribute attackDamage = resolveAttackDamageAttribute();
        if (attackDamage == null) {
            return 0.0D;
        }
        var modifiers = meta.getAttributeModifiers();
        if (modifiers == null) {
            return 0.0D;
        }
        Collection<AttributeModifier> attackModifiers = modifiers.get(attackDamage);
        if (attackModifiers == null || attackModifiers.isEmpty()) {
            return 0.0D;
        }

        double additive = 0.0D;
        double scalar = 0.0D;
        for (AttributeModifier modifier : attackModifiers) {
            if (modifier == null || !isMainHandModifier(modifier)) {
                continue;
            }
            String operation = modifier.getOperation().name();
            if ("ADD_NUMBER".equals(operation)) {
                additive += modifier.getAmount();
                continue;
            }
            if ("ADD_SCALAR".equals(operation) || "MULTIPLY_SCALAR_1".equals(operation)) {
                scalar += modifier.getAmount();
            }
        }
        return Math.max(0.0D, additive * (1.0D + scalar));
    }

    private boolean isMainHandModifier(AttributeModifier modifier) {
        try {
            Method getSlotGroup = modifier.getClass().getMethod("getSlotGroup");
            Object slotGroup = getSlotGroup.invoke(modifier);
            if (slotGroup != null) {
                String groupName = slotGroup.toString().toUpperCase();
                if (groupName.contains("MAINHAND")) {
                    return true;
                }
                if (groupName.contains("OFFHAND")) {
                    return false;
                }
                if (!groupName.contains("ANY")) {
                    return groupName.contains("HAND");
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall through to legacy slot lookup or permissive handling.
        }

        try {
            Method getSlot = modifier.getClass().getMethod("getSlot");
            Object slot = getSlot.invoke(modifier);
            if (slot != null) {
                String slotName = slot.toString().toUpperCase();
                if (slotName.contains("HAND")) {
                    return slotName.contains("MAIN") || !slotName.contains("OFF");
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // No legacy slot information available.
        }
        return true;
    }

    private Attribute resolveAttackDamageAttribute() {
        if (attackDamageAttributeResolved) {
            return attackDamageAttribute;
        }
        attackDamageAttributeResolved = true;
        for (String candidate : ATTACK_DAMAGE_ATTRIBUTE_NAMES) {
            try {
                Field field = Attribute.class.getField(candidate);
                Object value = field.get(null);
                if (value instanceof Attribute attribute) {
                    attackDamageAttribute = attribute;
                    return attackDamageAttribute;
                }
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                // Try the next compatible runtime field name.
            }
        }
        return null;
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    public record WeaponAttackSnapshot(
        int baseAttack,
        double weaponAttack,
        double combatBaseDamage
    ) {
    }

    private record AttackSelection(
        double value,
        String source
    ) {
    }
}
