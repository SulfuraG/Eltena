package com.eltena.core.application.equipment;

import com.eltena.core.domain.equipment.EquipmentDefinition;
import com.eltena.core.domain.player.PlayerProfile;
import com.eltena.core.domain.player.PlayerStatBonuses;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class EquipmentSystem {

    private static final String NBT_ITEM_CLASS = "io.lumine.mythic.lib.api.item.NBTItem";
    private static final String MMOITEMS_MATERIAL_ID_TAG = "MMOITEMS_MATERIAL_ID";

    private final EquipmentCatalog catalog;
    private final EquipmentLoader loader;
    private volatile Method nbtItemGet;
    private volatile Method nbtItemGetString;

    public EquipmentSystem(EquipmentCatalog catalog, EquipmentLoader loader) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    public int reload() {
        return loader.loadInto(catalog);
    }

    public PlayerStatBonuses resolveStatBonuses(PlayerProfile profile) {
        return resolveStatBonuses(profile, Set.of());
    }

    public PlayerStatBonuses resolveStatBonuses(PlayerProfile profile, Set<String> excludedItemIds) {
        if (profile == null) {
            return PlayerStatBonuses.none();
        }
        Player player = Bukkit.getPlayer(profile.playerId());
        if (player == null || !player.isOnline()) {
            return PlayerStatBonuses.none();
        }

        PlayerStatBonuses bonuses = PlayerStatBonuses.none();
        for (ItemStack stack : equippedItems(player)) {
            EquipmentDefinition definition = findDefinition(stack, excludedItemIds);
            if (definition != null) {
                bonuses = bonuses.add(definition.bonuses());
            }
        }
        return bonuses;
    }

    public List<SlotDebugEntry> debugEntries(PlayerProfile profile) {
        return debugEntries(profile, Set.of());
    }

    public List<SlotDebugEntry> debugEntries(PlayerProfile profile, Set<String> excludedItemIds) {
        if (profile == null) {
            return List.of();
        }
        Player player = Bukkit.getPlayer(profile.playerId());
        if (player == null || !player.isOnline()) {
            return List.of();
        }

        List<SlotDebugEntry> entries = new ArrayList<>(5);
        EntityEquipment equipment = player.getEquipment();
        if (equipment == null) {
            return entries;
        }
        collect(entries, "mainhand", equipment.getItemInMainHand(), excludedItemIds);
        collect(entries, "offhand", equipment.getItemInOffHand(), excludedItemIds);
        collect(entries, "helmet", equipment.getHelmet(), excludedItemIds);
        collect(entries, "chestplate", equipment.getChestplate(), excludedItemIds);
        collect(entries, "leggings", equipment.getLeggings(), excludedItemIds);
        collect(entries, "boots", equipment.getBoots(), excludedItemIds);
        return entries;
    }

    public String debugSummary(PlayerProfile profile) {
        return debugSummary(profile, Set.of());
    }

    public String debugSummary(PlayerProfile profile, Set<String> excludedItemIds) {
        return formatBonuses(resolveStatBonuses(profile, excludedItemIds));
    }

    private EquipmentDefinition findDefinition(ItemStack stack) {
        return findDefinition(stack, Set.of());
    }

    private EquipmentDefinition findDefinition(ItemStack stack, Set<String> excludedItemIds) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        String itemId = stack.getType().getKey().toString().toLowerCase();
        if (excludedItemIds != null && excludedItemIds.contains(itemId)) {
            return null;
        }
        return catalog.findByItemId(itemId);
    }

    private void collect(List<SlotDebugEntry> entries, String slot, ItemStack stack, Set<String> excludedItemIds) {
        String itemId = resolveItemId(stack);
        if (excludedItemIds != null && excludedItemIds.contains(itemId)) {
            entries.add(new SlotDebugEntry(slot, itemId, "skipped-mmoitems"));
            return;
        }
        EquipmentDefinition definition = findDefinition(stack, excludedItemIds);
        entries.add(new SlotDebugEntry(slot, itemId, definition == null ? "none" : definition.id()));
    }

    private List<ItemStack> equippedItems(Player player) {
        List<ItemStack> items = new ArrayList<>(5);
        EntityEquipment equipment = player.getEquipment();
        if (equipment == null) {
            return items;
        }
        items.add(equipment.getItemInMainHand());
        items.add(equipment.getItemInOffHand());
        items.add(equipment.getHelmet());
        items.add(equipment.getChestplate());
        items.add(equipment.getLeggings());
        items.add(equipment.getBoots());
        return items;
    }

    private String formatBonuses(PlayerStatBonuses bonuses) {
        List<String> entries = new ArrayList<>();
        append(entries, "def", bonuses.defense());
        return entries.isEmpty() ? "none" : String.join(" ", entries);
    }

    private void append(List<String> entries, String key, double value) {
        if (Math.abs(value) < 0.000001D) {
            return;
        }
        entries.add(key + ":" + (value >= 0.0D ? "+" : "") + value);
    }

    private String resolveItemId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "minecraft:air";
        }
        String tagged = readMmoItemsMaterialId(stack);
        if (tagged != null && !tagged.isBlank()) {
            return tagged.toLowerCase();
        }
        return stack.getType().getKey().toString().toLowerCase();
    }

    private String readMmoItemsMaterialId(ItemStack stack) {
        try {
            Method localGet = ensureNbtItemGet();
            Method localGetString = ensureNbtItemGetString();
            Object nbt = localGet.invoke(null, stack);
            Object value = localGetString.invoke(nbt, MMOITEMS_MATERIAL_ID_TAG);
            return value instanceof String text && !text.isBlank() ? text.trim() : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private Method ensureNbtItemGet() {
        Method current = nbtItemGet;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = nbtItemGet;
            if (current != null) {
                return current;
            }
            try {
                Class<?> nbtItemClass = Class.forName(NBT_ITEM_CLASS);
                nbtItemGet = current = nbtItemClass.getMethod("get", ItemStack.class);
                return current;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private Method ensureNbtItemGetString() {
        Method current = nbtItemGetString;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = nbtItemGetString;
            if (current != null) {
                return current;
            }
            try {
                Class<?> nbtItemClass = Class.forName(NBT_ITEM_CLASS);
                nbtItemGetString = current = nbtItemClass.getMethod("getString", String.class);
                return current;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    public record SlotDebugEntry(
        String slot,
        String itemId,
        String matchedDefinitionId
    ) {
    }
}
