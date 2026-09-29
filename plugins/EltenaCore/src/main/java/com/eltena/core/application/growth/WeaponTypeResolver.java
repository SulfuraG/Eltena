package com.eltena.core.application.growth;

import com.eltena.core.application.equipment.MmoItemsEquipmentStatsProvider;
import com.eltena.core.domain.stats.WeaponType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Objects;

public final class WeaponTypeResolver {

    private final MmoItemsEquipmentStatsProvider mmoItemsEquipmentStatsProvider;

    public WeaponTypeResolver(MmoItemsEquipmentStatsProvider mmoItemsEquipmentStatsProvider) {
        this.mmoItemsEquipmentStatsProvider = Objects.requireNonNull(mmoItemsEquipmentStatsProvider, "mmoItemsEquipmentStatsProvider");
    }

    public WeaponType resolve(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return WeaponType.FIST;
        }

        String category = resolveCategoryId(stack);
        if (category != null) {
            return switch (category) {
                case "SWORD" -> WeaponType.SWORD;
                case "GREATSWORD", "LONG_SWORD", "KATANA" -> WeaponType.GREATSWORD;
                case "AXE", "GREATAXE", "HAMMER", "GREATHAMMER" -> WeaponType.AXE;
                case "SPEAR", "LANCE", "HALBERD" -> WeaponType.SPEAR;
                case "BOW", "CROSSBOW", "MUSKET" -> WeaponType.BOW;
                case "STAFF", "WAND", "GREATSTAFF", "TOME", "CATALYST", "OFF_CATALYST", "MAIN_CATALYST" -> WeaponType.STAFF;
                case "DAGGER", "THRUSTING_SWORD" -> WeaponType.DAGGER;
                case "GAUNTLET" -> WeaponType.FIST;
                default -> fallbackResolve(stack);
            };
        }

        return fallbackResolve(stack);
    }

    public String resolveCategoryId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        String category = mmoItemsEquipmentStatsProvider.resolveWeaponCategory(stack);
        return category == null ? null : category.trim().toUpperCase(Locale.ROOT);
    }

    private WeaponType fallbackResolve(ItemStack stack) {
        Material material = stack.getType();
        String name = material.name();
        if (name.endsWith("_SWORD")) {
            return WeaponType.SWORD;
        }
        if (name.endsWith("_AXE")) {
            return WeaponType.AXE;
        }
        if (material == Material.BOW || material == Material.CROSSBOW) {
            return WeaponType.BOW;
        }
        if (material == Material.TRIDENT) {
            return WeaponType.SPEAR;
        }
        if (material == Material.STICK || material == Material.BLAZE_ROD) {
            return WeaponType.STAFF;
        }
        return WeaponType.OTHER;
    }
}
