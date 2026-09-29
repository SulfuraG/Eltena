package com.eltena.core.application.equipment;

import com.eltena.core.application.logging.CoreLoggingSettings;
import com.eltena.core.domain.player.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MmoItemsEquipmentStatsProvider {

    private static final String MMOITEMS_PLUGIN = "MMOItems";
    private static final String NBT_ITEM_CLASS = "io.lumine.mythic.lib.api.item.NBTItem";
    private static final String MMOITEMS_ITEM_ID_TAG = "MMOITEMS_ITEM_ID";
    private static final String MMOITEMS_ITEM_TYPE_TAG = "MMOITEMS_ITEM_TYPE";
    private static final String MMOITEMS_MATERIAL_ID_TAG = "MMOITEMS_MATERIAL_ID";
    private static final String TAG_ATTACK_DAMAGE = "MMOITEMS_ATTACK_DAMAGE";
    private static final String TAG_DEFENSE = "MMOITEMS_DEFENSE";
    private static final String TAG_MAX_HEALTH = "MMOITEMS_MAX_HEALTH";
    private static final String[] MAX_HEALTH_ATTRIBUTE_NAMES = {
        "MAX_HEALTH",
        "GENERIC_MAX_HEALTH"
    };

    private final JavaPlugin plugin;
    private final AtomicBoolean failureLogged = new AtomicBoolean(false);
    private volatile Bridge bridge;
    private volatile Attribute maxHealthAttribute;
    private volatile boolean maxHealthAttributeResolved;

    public MmoItemsEquipmentStatsProvider(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public String statusLine() {
        return initializeBridge().statusLine();
    }

    public MmoItemsEquipmentBonuses resolve(PlayerProfile profile) {
        if (profile == null) {
            return MmoItemsEquipmentBonuses.none();
        }
        Player player = Bukkit.getPlayer(profile.playerId());
        if (player == null || !player.isOnline()) {
            return MmoItemsEquipmentBonuses.none();
        }
        return resolve(player);
    }

    public MmoItemsEquipmentBonuses resolve(Player player) {
        if (player == null || !player.isOnline()) {
            return MmoItemsEquipmentBonuses.none();
        }
        boolean debug = CoreLoggingSettings.mmoItemsDebugEnabled(plugin);
        List<EquippedSlot> stacks = collectStacks(player);
        double mainHandWeaponDamage = 0.0D;
        double totalDefense = 0.0D;
        double totalMaxHealth = 0.0D;
        LinkedHashSet<String> resolvedItemIds = new LinkedHashSet<>();
        for (EquippedSlot slot : stacks) {
            EquippedStatRead equipped = resolveEquipped(slot);
            MmoItemsEquipmentBonuses resolved = equipped.bonuses();
            if ("mainhand".equals(slot.slot())) {
                mainHandWeaponDamage = resolved.weaponDamage();
            }
            totalDefense += resolved.defense();
            totalMaxHealth += resolved.maxHealthBonus();
            resolvedItemIds.addAll(resolved.resolvedItemIds());
            if (debug) {
                plugin.getLogger().info(
                    "[EltenaCore] MMOItems装備読取:"
                        + " player=" + player.getName()
                        + " slot=" + slot.slot()
                        + " material=" + resolveMaterialId(slot.stack())
                        + " itemType=" + safeText(resolveItemType(slot.stack()))
                        + " itemId=" + safeText(resolveItemId(slot.stack()))
                        + " attackDamage=" + resolved.weaponDamage()
                        + " rawDefense=" + resolved.defense()
                        + " rawMaxHealth=" + equipped.rawMaxHealth()
                        + " appliedMaxHealth=" + resolved.maxHealthBonus()
                        + " skipMaxHealthByAttribute=" + equipped.maxHealthHandledByAttribute()
                );
            }
        }
        if (debug) {
            plugin.getLogger().info(
                "[EltenaCore] MMOItems装備合計:"
                    + " player=" + player.getName()
                    + " equipmentDefense=" + totalDefense
                    + " equipmentMaxHealth=" + totalMaxHealth
                    + " mainHandWeaponDamage=" + mainHandWeaponDamage
                    + " resolvedIds=" + resolvedItemIds
            );
        }
        return new MmoItemsEquipmentBonuses(
            mainHandWeaponDamage,
            totalDefense,
            totalMaxHealth,
            resolvedItemIds
        );
    }

    public MmoItemsEquipmentBonuses resolve(ItemStack stack) {
        return readRaw(stack).toBonuses();
    }

    public String resolveWeaponCategory(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        Bridge activeBridge = initializeBridge();
        if (!activeBridge.available()) {
            return null;
        }
        try {
            Object nbt = activeBridge.nbtItemStaticGet().invoke(null, stack);
            if (nbt == null) {
                return null;
            }
            return normalizeCategory(readString(activeBridge, nbt, MMOITEMS_ITEM_TYPE_TAG));
        } catch (ReflectiveOperationException exception) {
            logFailureOnce("weapon-category failure: " + exception.getClass().getSimpleName() + " " + safeMessage(exception));
            return null;
        }
    }

    public String resolveItemId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        Bridge activeBridge = initializeBridge();
        if (!activeBridge.available()) {
            return null;
        }
        try {
            Object nbt = activeBridge.nbtItemStaticGet().invoke(null, stack);
            if (nbt == null) {
                return null;
            }
            return readString(activeBridge, nbt, MMOITEMS_ITEM_ID_TAG);
        } catch (ReflectiveOperationException exception) {
            logFailureOnce("item-id failure: " + exception.getClass().getSimpleName() + " " + safeMessage(exception));
            return null;
        }
    }

    public String resolveItemType(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        Bridge activeBridge = initializeBridge();
        if (!activeBridge.available()) {
            return null;
        }
        try {
            Object nbt = activeBridge.nbtItemStaticGet().invoke(null, stack);
            if (nbt == null) {
                return null;
            }
            return readString(activeBridge, nbt, MMOITEMS_ITEM_TYPE_TAG);
        } catch (ReflectiveOperationException exception) {
            logFailureOnce("item-type failure: " + exception.getClass().getSimpleName() + " " + safeMessage(exception));
            return null;
        }
    }

    public String resolveMaterialId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "minecraft:air";
        }
        Bridge activeBridge = initializeBridge();
        if (!activeBridge.available()) {
            return stack.getType().getKey().toString().toLowerCase(Locale.ROOT);
        }
        try {
            Object nbt = activeBridge.nbtItemStaticGet().invoke(null, stack);
            return resolveMaterialId(activeBridge, nbt, stack);
        } catch (ReflectiveOperationException exception) {
            logFailureOnce("material-id failure: " + exception.getClass().getSimpleName() + " " + safeMessage(exception));
            return stack.getType().getKey().toString().toLowerCase(Locale.ROOT);
        }
    }

    private EquippedStatRead resolveEquipped(EquippedSlot slot) {
        RawStatRead raw = readRaw(slot.stack());
        if (raw.isEmpty()) {
            return new EquippedStatRead(MmoItemsEquipmentBonuses.none(), 0.0D, false);
        }
        boolean maxHealthHandledByAttribute = hasRelevantAttributeModifier(slot.stack(), slot.slot(), resolveMaxHealthAttribute());
        return new EquippedStatRead(
            new MmoItemsEquipmentBonuses(
                raw.weaponDamage(),
                raw.defense(),
                maxHealthHandledByAttribute ? 0.0D : raw.maxHealthBonus(),
                raw.resolvedItemIds()
            ),
            raw.maxHealthBonus(),
            maxHealthHandledByAttribute
        );
    }

    private RawStatRead readRaw(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return RawStatRead.empty();
        }
        Bridge activeBridge = initializeBridge();
        if (!activeBridge.available()) {
            return RawStatRead.empty();
        }
        try {
            Object nbt = activeBridge.nbtItemStaticGet().invoke(null, stack);
            if (nbt == null) {
                return RawStatRead.empty();
            }
            String itemId = readString(activeBridge, nbt, MMOITEMS_ITEM_ID_TAG);
            String itemType = readString(activeBridge, nbt, MMOITEMS_ITEM_TYPE_TAG);
            String taggedMaterialId = readString(activeBridge, nbt, MMOITEMS_MATERIAL_ID_TAG);
            String materialId = hasText(taggedMaterialId)
                ? taggedMaterialId.trim().toLowerCase(Locale.ROOT)
                : stack.getType().getKey().toString().toLowerCase(Locale.ROOT);
            boolean recognized = hasText(itemId) || hasText(itemType) || hasText(taggedMaterialId);
            if (!recognized) {
                return RawStatRead.empty();
            }

            LinkedHashSet<String> resolvedIds = new LinkedHashSet<>();
            if (hasText(itemId)) {
                resolvedIds.add(itemId);
            }
            resolvedIds.add(materialId);

            return new RawStatRead(
                readDouble(activeBridge, nbt, TAG_ATTACK_DAMAGE),
                readDouble(activeBridge, nbt, TAG_DEFENSE),
                readDouble(activeBridge, nbt, TAG_MAX_HEALTH),
                resolvedIds
            );
        } catch (ReflectiveOperationException exception) {
            logFailureOnce("provider failure: " + exception.getClass().getSimpleName() + " " + safeMessage(exception));
            return RawStatRead.empty();
        }
    }

    private List<EquippedSlot> collectStacks(Player player) {
        PlayerInventory inventory = player.getInventory();
        List<EquippedSlot> stacks = new ArrayList<>(6);
        add(stacks, "mainhand", inventory.getItemInMainHand());
        add(stacks, "offhand", inventory.getItemInOffHand());
        add(stacks, "helmet", inventory.getHelmet());
        add(stacks, "chestplate", inventory.getChestplate());
        add(stacks, "leggings", inventory.getLeggings());
        add(stacks, "boots", inventory.getBoots());
        return stacks;
    }

    private void add(List<EquippedSlot> stacks, String slot, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        stacks.add(new EquippedSlot(slot, stack));
    }

    private Bridge initializeBridge() {
        Bridge current = bridge;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = bridge;
            if (current != null) {
                return current;
            }
            bridge = current = createBridge();
            return current;
        }
    }

    private Bridge createBridge() {
        Plugin mmoItems = plugin.getServer().getPluginManager().getPlugin(MMOITEMS_PLUGIN);
        if (mmoItems == null) {
            return Bridge.unavailable("MMOItems plugin not found");
        }
        if (!mmoItems.isEnabled()) {
            return Bridge.unavailable("MMOItems plugin is not enabled");
        }
        try {
            ClassLoader loader = mmoItems.getClass().getClassLoader();
            Class<?> nbtItemClass = Class.forName(NBT_ITEM_CLASS, true, loader);
            Method nbtItemStaticGet = nbtItemClass.getMethod("get", ItemStack.class);
            Method nbtItemGetString = nbtItemClass.getMethod("getString", String.class);
            Method nbtItemGetDouble = nbtItemClass.getMethod("getDouble", String.class);
            return new Bridge(true, "enabled", nbtItemStaticGet, nbtItemGetString, nbtItemGetDouble);
        } catch (ReflectiveOperationException exception) {
            logFailureOnce("bridge init failure: " + exception.getClass().getSimpleName() + " " + safeMessage(exception));
            return Bridge.unavailable("reflection init failed");
        }
    }

    private String resolveMaterialId(Bridge bridge, Object nbt, ItemStack stack) throws ReflectiveOperationException {
        String tagged = readString(bridge, nbt, MMOITEMS_MATERIAL_ID_TAG);
        if (hasText(tagged)) {
            return tagged.toLowerCase(Locale.ROOT);
        }
        return stack.getType().getKey().toString().toLowerCase(Locale.ROOT);
    }

    private double readDouble(Bridge bridge, Object nbt, String key) throws ReflectiveOperationException {
        Object value = bridge.nbtItemGetDouble().invoke(nbt, key);
        return value instanceof Number number ? number.doubleValue() : 0.0D;
    }

    private String readString(Bridge bridge, Object nbt, String key) throws ReflectiveOperationException {
        Object value = bridge.nbtItemGetString().invoke(nbt, key);
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private boolean hasRelevantAttributeModifier(ItemStack stack, String slot, Attribute attribute) {
        if (attribute == null || stack == null || stack.getType().isAir()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasAttributeModifiers()) {
            return false;
        }
        var modifiers = meta.getAttributeModifiers();
        if (modifiers == null) {
            return false;
        }
        var scoped = modifiers.get(attribute);
        if (scoped == null || scoped.isEmpty()) {
            return false;
        }
        for (AttributeModifier modifier : scoped) {
            if (modifier == null) {
                continue;
            }
            if (modifierMatchesSlot(modifier, slot)) {
                return true;
            }
        }
        return false;
    }

    private boolean modifierMatchesSlot(AttributeModifier modifier, String slot) {
        if (slot == null || slot.isBlank()) {
            return true;
        }
        String normalizedSlot = slot.trim().toLowerCase(Locale.ROOT);
        String groupName = readSlotGroup(modifier);
        if (groupName != null) {
            return groupMatchesSlot(groupName, normalizedSlot);
        }
        String slotName = readLegacySlot(modifier);
        if (slotName != null) {
            return groupMatchesSlot(slotName, normalizedSlot);
        }
        return true;
    }

    private String readSlotGroup(AttributeModifier modifier) {
        try {
            Method getSlotGroup = modifier.getClass().getMethod("getSlotGroup");
            Object slotGroup = getSlotGroup.invoke(modifier);
            return slotGroup == null ? null : slotGroup.toString().toUpperCase(Locale.ROOT);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private String readLegacySlot(AttributeModifier modifier) {
        try {
            Method getSlot = modifier.getClass().getMethod("getSlot");
            Object slot = getSlot.invoke(modifier);
            return slot == null ? null : slot.toString().toUpperCase(Locale.ROOT);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private boolean groupMatchesSlot(String groupName, String slot) {
        if (groupName == null || groupName.isBlank()) {
            return true;
        }
        if (groupName.contains("ANY")) {
            return true;
        }
        return switch (slot) {
            case "mainhand" -> groupName.contains("MAINHAND") || (groupName.contains("HAND") && !groupName.contains("OFFHAND"));
            case "offhand" -> groupName.contains("OFFHAND");
            case "helmet" -> groupName.contains("HEAD") || groupName.contains("HELMET");
            case "chestplate" -> groupName.contains("CHEST");
            case "leggings" -> groupName.contains("LEG");
            case "boots" -> groupName.contains("FEET") || groupName.contains("BOOT");
            default -> true;
        };
    }

    private Attribute resolveMaxHealthAttribute() {
        if (maxHealthAttributeResolved) {
            return maxHealthAttribute;
        }
        maxHealthAttributeResolved = true;
        maxHealthAttribute = resolveAttribute(MAX_HEALTH_ATTRIBUTE_NAMES);
        return maxHealthAttribute;
    }

    private Attribute resolveAttribute(String[] candidates) {
        for (String candidate : candidates) {
            try {
                Field field = Attribute.class.getField(candidate);
                Object value = field.get(null);
                if (value instanceof Attribute attribute) {
                    return attribute;
                }
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                // Try the next compatible runtime field name.
            }
        }
        return null;
    }

    private String normalizeCategory(String raw) {
        if (!hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeText(String value) {
        return hasText(value) ? value : "-";
    }

    private void logFailureOnce(String message) {
        if (failureLogged.compareAndSet(false, true)) {
            plugin.getLogger().warning("[EltenaCore] MMOItems equipment provider unavailable: " + message);
        }
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? "(no message)" : exception.getMessage();
    }

    private record Bridge(
        boolean available,
        String statusLine,
        Method nbtItemStaticGet,
        Method nbtItemGetString,
        Method nbtItemGetDouble
    ) {
        private static Bridge unavailable(String statusLine) {
            return new Bridge(false, statusLine, null, null, null);
        }
    }

    private record EquippedSlot(
        String slot,
        ItemStack stack
    ) {
    }

    private record RawStatRead(
        double weaponDamage,
        double defense,
        double maxHealthBonus,
        Set<String> resolvedItemIds
    ) {
        private static RawStatRead empty() {
            return new RawStatRead(0.0D, 0.0D, 0.0D, Set.of());
        }

        private boolean isEmpty() {
            return resolvedItemIds == null || resolvedItemIds.isEmpty();
        }

        private MmoItemsEquipmentBonuses toBonuses() {
            return new MmoItemsEquipmentBonuses(
                weaponDamage,
                defense,
                maxHealthBonus,
                resolvedItemIds == null ? Set.of() : resolvedItemIds
            );
        }
    }

    private record EquippedStatRead(
        MmoItemsEquipmentBonuses bonuses,
        double rawMaxHealth,
        boolean maxHealthHandledByAttribute
    ) {
    }
}
