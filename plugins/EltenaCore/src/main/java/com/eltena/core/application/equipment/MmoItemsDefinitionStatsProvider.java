package com.eltena.core.application.equipment;

import com.eltena.core.application.logging.CoreLoggingSettings;
import com.eltena.core.domain.player.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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

public final class MmoItemsDefinitionStatsProvider {

    private static final String MMOITEMS_PLUGIN = "MMOItems";
    private static final String TYPE_CLASS = "net.Indyuce.mmoitems.api.Type";
    private static final String MMOITEMS_MAIN_CLASS = "net.Indyuce.mmoitems.MMOItems";
    private static final String MMO_ITEM_CLASS = "net.Indyuce.mmoitems.api.item.mmoitem.MMOItem";
    private static final String ITEM_STATS_CLASS = "net.Indyuce.mmoitems.ItemStats";
    private static final String ITEM_STAT_CLASS = "net.Indyuce.mmoitems.stat.type.ItemStat";
    private static final String DOUBLE_DATA_CLASS = "net.Indyuce.mmoitems.stat.data.DoubleData";

    private final JavaPlugin plugin;
    private final MmoItemsDefinitionMappingRepository repository;
    private final AtomicBoolean failureLogged = new AtomicBoolean(false);
    private volatile Bridge bridge;

    public MmoItemsDefinitionStatsProvider(
        JavaPlugin plugin,
        MmoItemsDefinitionMappingRepository repository
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public String statusLine() {
        Bridge activeBridge = initializeBridge();
        if (!activeBridge.available()) {
            return activeBridge.statusLine();
        }
        return activeBridge.statusLine() + " mappings=" + repository.size();
    }

    public MmoItemsDefinitionBonuses resolve(PlayerProfile profile) {
        if (profile == null) {
            return MmoItemsDefinitionBonuses.none();
        }
        Player player = Bukkit.getPlayer(profile.playerId());
        if (player == null || !player.isOnline()) {
            return MmoItemsDefinitionBonuses.none();
        }
        return resolve(player);
    }

    public MmoItemsDefinitionBonuses resolve(Player player) {
        if (player == null || !player.isOnline()) {
            return MmoItemsDefinitionBonuses.none();
        }
        Bridge activeBridge = initializeBridge();
        if (!activeBridge.available()) {
            return MmoItemsDefinitionBonuses.none();
        }

        boolean lookupDebug = CoreLoggingSettings.mmoItemsDefinitionLookupDebugEnabled(plugin);
        boolean statsDebug = CoreLoggingSettings.mmoItemsDefinitionStatsDebugEnabled(plugin);
        double defense = 0.0D;
        Set<String> mappedItemIds = new LinkedHashSet<>();

        for (SlotStack slotStack : collectStacks(player)) {
            MmoItemsDefinitionReference reference = repository.find(slotStack.modItemId());
            if (lookupDebug) {
                plugin.getLogger().info(
                    "[EltenaCore] MMOItems Definition lookup: player=" + player.getName()
                        + " slot=" + slotStack.slot()
                        + " item=" + slotStack.modItemId()
                        + " mapping=" + (reference == null ? "none" : reference.displayKey())
                );
            }
            if (reference == null) {
                continue;
            }
            try {
                Object type = activeBridge.typeGet().invoke(null, reference.type());
                if (type == null) {
                    if (lookupDebug) {
                        plugin.getLogger().info(
                            "[EltenaCore] MMOItems Definition missing type: player=" + player.getName()
                                + " slot=" + slotStack.slot()
                                + " mapping=" + reference.displayKey()
                        );
                    }
                    continue;
                }
                Object mmoItem = activeBridge.mmoItemsGetMmoItem().invoke(activeBridge.mmoItemsPlugin(), type, reference.id());
                if (mmoItem == null) {
                    if (lookupDebug) {
                        plugin.getLogger().info(
                            "[EltenaCore] MMOItems Definition missing item: player=" + player.getName()
                                + " slot=" + slotStack.slot()
                                + " mapping=" + reference.displayKey()
                        );
                    }
                    continue;
                }

                double defenseRaw = readStat(activeBridge, mmoItem, activeBridge.defenseStat());
                if (statsDebug) {
                    plugin.getLogger().info(
                        "[EltenaCore] MMOItems Definition stats: player=" + player.getName()
                            + " slot=" + slotStack.slot()
                            + " mapping=" + reference.displayKey()
                            + " defense=" + defenseRaw
                    );
                }

                defense += defenseRaw;
                mappedItemIds.add(slotStack.modItemId());
            } catch (ReflectiveOperationException exception) {
                logFailureOnce("stat-read failure: " + exception.getClass().getSimpleName() + " " + safeMessage(exception));
                return MmoItemsDefinitionBonuses.none();
            }
        }

        if (mappedItemIds.isEmpty()) {
            return MmoItemsDefinitionBonuses.none();
        }
        return new MmoItemsDefinitionBonuses(defense, mappedItemIds);
    }

    private List<SlotStack> collectStacks(Player player) {
        PlayerInventory inventory = player.getInventory();
        List<SlotStack> stacks = new ArrayList<>(6);
        add(stacks, "mainhand", inventory.getItemInMainHand());
        add(stacks, "offhand", inventory.getItemInOffHand());
        add(stacks, "helmet", inventory.getHelmet());
        add(stacks, "chestplate", inventory.getChestplate());
        add(stacks, "leggings", inventory.getLeggings());
        add(stacks, "boots", inventory.getBoots());
        return stacks;
    }

    private void add(List<SlotStack> stacks, String slot, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        stacks.add(new SlotStack(slot, stack.getType().getKey().toString().toLowerCase(Locale.ROOT)));
    }

    private double readStat(Bridge bridge, Object mmoItem, Object itemStat) throws ReflectiveOperationException {
        Object data = bridge.mmoItemComputeData().invoke(mmoItem, itemStat);
        if (data == null) {
            data = bridge.mmoItemGetData().invoke(mmoItem, itemStat);
        }
        if (data == null) {
            return 0.0D;
        }
        if (bridge.doubleDataClass().isInstance(data)) {
            Object value = bridge.doubleDataGetValue().invoke(data);
            return value instanceof Number number ? number.doubleValue() : 0.0D;
        }
        if (data instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0D;
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
            Class<?> typeClass = Class.forName(TYPE_CLASS, true, loader);
            Class<?> mmoItemsMainClass = Class.forName(MMOITEMS_MAIN_CLASS, true, loader);
            Class<?> mmoItemClass = Class.forName(MMO_ITEM_CLASS, true, loader);
            Class<?> itemStatsClass = Class.forName(ITEM_STATS_CLASS, true, loader);
            Class<?> itemStatClass = Class.forName(ITEM_STAT_CLASS, true, loader);
            Class<?> doubleDataClass = Class.forName(DOUBLE_DATA_CLASS, true, loader);

            Method typeGet = typeClass.getMethod("get", String.class);
            Method mmoItemsGetMmoItem = mmoItemsMainClass.getMethod("getMMOItem", typeClass, String.class);
            Method mmoItemGetData = mmoItemClass.getMethod("getData", itemStatClass);
            Method mmoItemComputeData = mmoItemClass.getMethod("computeData", itemStatClass);
            Method doubleDataGetValue = doubleDataClass.getMethod("getValue");

            Field defenseStat = itemStatsClass.getField("DEFENSE");
            return new Bridge(
                true,
                "enabled",
                mmoItems,
                typeGet,
                mmoItemsGetMmoItem,
                mmoItemGetData,
                mmoItemComputeData,
                doubleDataClass,
                doubleDataGetValue,
                defenseStat.get(null)
            );
        } catch (ReflectiveOperationException exception) {
            logFailureOnce("bridge init failure: " + exception.getClass().getSimpleName() + " " + safeMessage(exception));
            return Bridge.unavailable("reflection init failed");
        }
    }

    private void logFailureOnce(String message) {
        if (failureLogged.compareAndSet(false, true)) {
            plugin.getLogger().warning("[EltenaCore] MMOItems definition provider unavailable: " + message);
        }
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? "(no message)" : exception.getMessage();
    }

    private record SlotStack(
        String slot,
        String modItemId
    ) {
    }

    private record Bridge(
        boolean available,
        String statusLine,
        Plugin mmoItemsPlugin,
        Method typeGet,
        Method mmoItemsGetMmoItem,
        Method mmoItemGetData,
        Method mmoItemComputeData,
        Class<?> doubleDataClass,
        Method doubleDataGetValue,
        Object defenseStat
    ) {
        private static Bridge unavailable(String statusLine) {
            return new Bridge(false, statusLine, null, null, null, null, null, null, null, null);
        }
    }
}
