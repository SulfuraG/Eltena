package com.eltena.addon.client.item;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class CleanModMaterialTags {
    public static final String CLEAN_TAG = "MMOITEMS_CLEAN_MOD_MATERIAL";
    public static final String DISABLE_MOD_TOOLTIP_TAG = "MMOITEMS_DISABLE_MOD_TOOLTIP";

    private CleanModMaterialTags() {
    }

    public static boolean shouldSuppressModTooltip(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CompoundTag tag = readCustomData(stack);
        return tag != null
            && ((tag.contains(CLEAN_TAG) && tag.getBoolean(CLEAN_TAG))
            || (tag.contains(DISABLE_MOD_TOOLTIP_TAG) && tag.getBoolean(DISABLE_MOD_TOOLTIP_TAG)));
    }

    public static boolean hasCleanTag(ItemStack stack) {
        CompoundTag tag = readCustomData(stack);
        return tag != null && tag.contains(CLEAN_TAG) && tag.getBoolean(CLEAN_TAG);
    }

    public static boolean hasDisableModTooltipTag(ItemStack stack) {
        CompoundTag tag = readCustomData(stack);
        return tag != null && tag.contains(DISABLE_MOD_TOOLTIP_TAG) && tag.getBoolean(DISABLE_MOD_TOOLTIP_TAG);
    }

    public static String registryId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "<empty>";
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "<unknown>" : key.toString();
    }

    public static String topLevelKeys(ItemStack stack) {
        CompoundTag tag = readCustomData(stack);
        if (tag == null || tag.isEmpty()) {
            return "<none>";
        }
        Set<String> keys = new TreeSet<>(tag.getAllKeys());
        return keys.isEmpty() ? "<none>" : keys.stream().collect(Collectors.joining(","));
    }

    public static boolean shouldLogCandidate(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return hasCleanTag(stack) || hasDisableModTooltipTag(stack);
    }

    public static CompoundTag readCustomData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData == null) {
            return null;
        }
        CompoundTag tag = customData.copyTag();
        return tag == null || tag.isEmpty() ? null : tag;
    }
}
