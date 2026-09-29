package com.eltena.core.application.equipment;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ArmorDefenseSnapshotResolver {

    private static final String[] ARMOR_ATTRIBUTE_NAMES = {
        "ARMOR",
        "GENERIC_ARMOR"
    };

    private volatile Attribute armorAttribute;
    private volatile boolean armorAttributeResolved;

    public DefenseSnapshot resolve(Player player, MmoItemsEquipmentStatsProvider provider) {
        if (player == null || !player.isOnline()) {
            return DefenseSnapshot.none();
        }
        Attribute armor = resolveArmorAttribute();
        List<SlotDefenseEntry> entries = collectEntries(player, provider, armor);
        double vanillaArmorDefense = 0.0D;
        double modArmorDefense = 0.0D;
        boolean hasVanilla = false;
        boolean hasMod = false;
        for (SlotDefenseEntry entry : entries) {
            if (entry.isModded()) {
                modArmorDefense += entry.slotDefense();
                hasMod = true;
            } else {
                vanillaArmorDefense += entry.slotDefense();
                hasVanilla = true;
            }
        }
        double liveArmorDefense = resolveLiveArmorDefense(player, armor);
        double categorizedArmorDefense = vanillaArmorDefense + modArmorDefense;
        double delta = liveArmorDefense - categorizedArmorDefense;
        if (Math.abs(delta) > 0.000001D) {
            if (hasMod && !hasVanilla) {
                modArmorDefense += delta;
            } else {
                vanillaArmorDefense += delta;
            }
        }
        return new DefenseSnapshot(
            Math.max(0.0D, liveArmorDefense),
            Math.max(0.0D, vanillaArmorDefense),
            Math.max(0.0D, modArmorDefense),
            List.copyOf(entries)
        );
    }

    public double resolveItemArmorDefense(ItemStack stack, String slotId) {
        return resolveItemArmorDefense(stack, slotId, resolveArmorAttribute());
    }

    private List<SlotDefenseEntry> collectEntries(Player player, MmoItemsEquipmentStatsProvider provider, Attribute armor) {
        PlayerInventory inventory = player.getInventory();
        List<SlotDefenseEntry> entries = new ArrayList<>(6);
        add(entries, "mainhand", inventory.getItemInMainHand(), provider, armor);
        add(entries, "offhand", inventory.getItemInOffHand(), provider, armor);
        add(entries, "helmet", inventory.getHelmet(), provider, armor);
        add(entries, "chestplate", inventory.getChestplate(), provider, armor);
        add(entries, "leggings", inventory.getLeggings(), provider, armor);
        add(entries, "boots", inventory.getBoots(), provider, armor);
        return entries;
    }

    private void add(
        List<SlotDefenseEntry> entries,
        String slotId,
        ItemStack stack,
        MmoItemsEquipmentStatsProvider provider,
        Attribute armor
    ) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        String materialId = provider.resolveMaterialId(stack);
        String namespace = namespaceOf(materialId);
        entries.add(new SlotDefenseEntry(
            slotId,
            materialId,
            provider.resolveItemType(stack),
            provider.resolveItemId(stack),
            resolveItemArmorDefense(stack, slotId, armor),
            !"minecraft".equals(namespace)
        ));
    }

    private double resolveItemArmorDefense(ItemStack stack, String slotId, Attribute armor) {
        if (stack == null || stack.getType().isAir() || armor == null) {
            return 0.0D;
        }
        ItemMeta meta = stack.getItemMeta();
        double fromMeta = sumArmorAttribute(meta, armor, slotId);
        if (Math.abs(fromMeta) > 0.000001D) {
            return Math.max(0.0D, fromMeta);
        }
        double fromDefaultMaterial = sumDefaultMaterialArmorAttribute(stack, armor, slotId);
        return Math.max(0.0D, fromDefaultMaterial);
    }

    private double resolveLiveArmorDefense(Player player, Attribute armor) {
        if (player == null || armor == null) {
            return 0.0D;
        }
        AttributeInstance instance = player.getAttribute(armor);
        if (instance == null) {
            return 0.0D;
        }
        return Math.max(0.0D, instance.getValue());
    }

    private Attribute resolveArmorAttribute() {
        if (armorAttributeResolved) {
            return armorAttribute;
        }
        armorAttributeResolved = true;
        for (String candidate : ARMOR_ATTRIBUTE_NAMES) {
            try {
                Object value = Attribute.class.getField(candidate).get(null);
                if (value instanceof Attribute attribute) {
                    armorAttribute = attribute;
                    return armorAttribute;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next runtime-compatible attribute name.
            }
        }
        return null;
    }

    private double sumArmorAttribute(ItemMeta meta, Attribute armor, String slotId) {
        if (meta == null || armor == null) {
            return 0.0D;
        }
        try {
            Method method = meta.getClass().getMethod("getAttributeModifiers", Attribute.class);
            Object result = method.invoke(meta, armor);
            double sum = sumModifierContainer(result, armor, slotId);
            if (Math.abs(sum) > 0.000001D) {
                return sum;
            }
        } catch (ReflectiveOperationException ignored) {
            // Fallback to generic attribute container lookup below.
        }
        try {
            Method method = meta.getClass().getMethod("getAttributeModifiers");
            Object result = method.invoke(meta);
            return sumModifierContainer(result, armor, slotId);
        } catch (ReflectiveOperationException ignored) {
            return 0.0D;
        }
    }

    private double sumDefaultMaterialArmorAttribute(ItemStack stack, Attribute armor, String slotId) {
        if (stack == null || armor == null) {
            return 0.0D;
        }
        try {
            Class<?> slotClass = Class.forName("org.bukkit.inventory.EquipmentSlot");
            Object slot = resolveEnumConstant(slotClass, toEquipmentSlotName(slotId));
            if (slot != null) {
                Method method = stack.getType().getClass().getMethod("getDefaultAttributeModifiers", slotClass);
                Object result = method.invoke(stack.getType(), slot);
                double sum = sumModifierContainer(result, armor, slotId);
                if (Math.abs(sum) > 0.000001D) {
                    return sum;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Try slot-group variant below.
        }
        try {
            Class<?> groupClass = Class.forName("org.bukkit.inventory.EquipmentSlotGroup");
            Object slotGroup = resolveEnumConstant(groupClass, toEquipmentSlotGroupName(slotId));
            if (slotGroup != null) {
                Method method = stack.getType().getClass().getMethod("getDefaultAttributeModifiers", groupClass);
                Object result = method.invoke(stack.getType(), slotGroup);
                return sumModifierContainer(result, armor, slotId);
            }
        } catch (ReflectiveOperationException ignored) {
            return 0.0D;
        }
        return 0.0D;
    }

    private Object resolveEnumConstant(Class<?> enumClass, String constantName) {
        if (enumClass == null || !enumClass.isEnum() || constantName == null || constantName.isBlank()) {
            return null;
        }
        for (Object constant : enumClass.getEnumConstants()) {
            if (constant != null && constant.toString().equalsIgnoreCase(constantName)) {
                return constant;
            }
        }
        return null;
    }

    private double sumModifierContainer(Object container, Attribute armor, String slotId) {
        if (container == null) {
            return 0.0D;
        }
        if (container instanceof Iterable<?> iterable) {
            return sumModifierIterable(iterable, slotId);
        }
        if (container instanceof Map<?, ?> map) {
            return sumModifierMap(map, armor, slotId);
        }
        try {
            Method entriesMethod = container.getClass().getMethod("entries");
            Object entries = entriesMethod.invoke(container);
            if (entries instanceof Iterable<?> iterable) {
                double sum = 0.0D;
                for (Object entryObject : iterable) {
                    if (entryObject instanceof Map.Entry<?, ?> entry
                        && Objects.equals(entry.getKey(), armor)
                        && entry.getValue() instanceof AttributeModifier modifier) {
                        sum += appliedAmount(modifier, slotId);
                    }
                }
                return sum;
            }
        } catch (ReflectiveOperationException ignored) {
            // Ignore containers without Multimap-style entries().
        }
        return 0.0D;
    }

    private double sumModifierMap(Map<?, ?> map, Attribute armor, String slotId) {
        double sum = 0.0D;
        Object value = map.get(armor);
        if (value instanceof Iterable<?> iterable) {
            sum += sumModifierIterable(iterable, slotId);
        } else if (value instanceof AttributeModifier modifier) {
            sum += appliedAmount(modifier, slotId);
        }
        return sum;
    }

    private double sumModifierIterable(Iterable<?> iterable, String slotId) {
        double sum = 0.0D;
        for (Object candidate : iterable) {
            if (candidate instanceof AttributeModifier modifier) {
                sum += appliedAmount(modifier, slotId);
            } else if (candidate instanceof Map.Entry<?, ?> entry && entry.getValue() instanceof AttributeModifier modifier) {
                sum += appliedAmount(modifier, slotId);
            }
        }
        return sum;
    }

    private double appliedAmount(AttributeModifier modifier, String slotId) {
        if (modifier == null || !modifierAppliesToSlot(modifier, slotId)) {
            return 0.0D;
        }
        try {
            Object operation = modifier.getClass().getMethod("getOperation").invoke(modifier);
            if (operation != null && !"ADD_NUMBER".equalsIgnoreCase(operation.toString())) {
                return 0.0D;
            }
        } catch (ReflectiveOperationException ignored) {
            // Ignore when runtime attribute modifiers do not expose operation.
        }
        return modifier.getAmount();
    }

    private boolean modifierAppliesToSlot(AttributeModifier modifier, String slotId) {
        try {
            Object slotGroup = modifier.getClass().getMethod("getSlotGroup").invoke(modifier);
            if (slotGroup != null) {
                return slotMatches(slotGroup.toString(), slotId);
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall through to older slot API.
        }
        try {
            Object slot = modifier.getClass().getMethod("getSlot").invoke(modifier);
            if (slot != null) {
                return slotMatches(slot.toString(), slotId);
            }
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
        return true;
    }

    private boolean slotMatches(String rawSlot, String slotId) {
        if (rawSlot == null || rawSlot.isBlank()) {
            return true;
        }
        String normalized = rawSlot.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (slotId) {
            case "mainhand" -> normalized.equals("ANY") || normalized.equals("HAND") || normalized.equals("MAINHAND") || normalized.equals("MAIN_HAND");
            case "offhand" -> normalized.equals("ANY") || normalized.equals("OFFHAND") || normalized.equals("OFF_HAND");
            case "helmet" -> normalized.equals("ANY") || normalized.equals("ARMOR") || normalized.equals("HEAD");
            case "chestplate" -> normalized.equals("ANY") || normalized.equals("ARMOR") || normalized.equals("CHEST");
            case "leggings" -> normalized.equals("ANY") || normalized.equals("ARMOR") || normalized.equals("LEGS");
            case "boots" -> normalized.equals("ANY") || normalized.equals("ARMOR") || normalized.equals("FEET");
            default -> true;
        };
    }

    private String toEquipmentSlotName(String slotId) {
        return switch (slotId) {
            case "mainhand" -> "HAND";
            case "offhand" -> "OFF_HAND";
            case "helmet" -> "HEAD";
            case "chestplate" -> "CHEST";
            case "leggings" -> "LEGS";
            case "boots" -> "FEET";
            default -> "HAND";
        };
    }

    private String toEquipmentSlotGroupName(String slotId) {
        return switch (slotId) {
            case "mainhand" -> "MAINHAND";
            case "offhand" -> "OFFHAND";
            case "helmet" -> "HEAD";
            case "chestplate" -> "CHEST";
            case "leggings" -> "LEGS";
            case "boots" -> "FEET";
            default -> "ANY";
        };
    }

    private String namespaceOf(String materialId) {
        if (materialId == null || materialId.isBlank()) {
            return "minecraft";
        }
        int separator = materialId.indexOf(':');
        if (separator <= 0) {
            return "minecraft";
        }
        return materialId.substring(0, separator).toLowerCase(Locale.ROOT);
    }

    public record DefenseSnapshot(
        double liveArmorDefense,
        double vanillaArmorDefense,
        double modArmorDefense,
        List<SlotDefenseEntry> slotEntries
    ) {
        public static DefenseSnapshot none() {
            return new DefenseSnapshot(0.0D, 0.0D, 0.0D, List.of());
        }

        public double totalArmorDefense() {
            return Math.max(0.0D, liveArmorDefense);
        }
    }

    public record SlotDefenseEntry(
        String slot,
        String materialId,
        String itemType,
        String itemId,
        double slotDefense,
        boolean isModded
    ) {
    }
}
