package com.eltena.core.admin;

import com.eltena.core.application.ui.MenuSystem;
import com.eltena.core.application.ui.MenuViewType;
import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.player.PlayerProfile;
import com.eltena.core.domain.stats.PlayerStats;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MainHand;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class MenuProtectionVerifier {

    private final ServiceRegistry services;

    public MenuProtectionVerifier(ServiceRegistry services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    public List<String> verify(String targetPlayerName) throws IOException {
        UUID targetId = UUID.nameUUIDFromBytes(("OfflinePlayer:" + targetPlayerName).getBytes(StandardCharsets.UTF_8));
        PlayerProfile profile = services.playerProfiles().loadOrCreate(targetId, targetPlayerName);
        PlayerStats stats = services.playerStats().loadOrCreate(targetId);
        MenuSystem menuSystem = services.menuSystem();
        List<String> messages = new ArrayList<>();

        for (MenuViewType type : MenuViewType.values()) {
            Inventory top = menuSystem.createProtectedInventory(profile, stats, type);
            Inventory bottom = Bukkit.createInventory(null, 36, "player");
            InventoryView view = createView(top, bottom, targetPlayerName);

            assertCancelled(new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL), type, "normal click");
            assertCancelled(new InventoryClickEvent(view, InventoryType.SlotType.QUICKBAR, top.getSize(), ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY), type, "shift click");
            assertCancelled(new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 1), type, "number key swap");
            assertCancelled(new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP), type, "offhand swap");
            assertCancelled(new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR), type, "double click collect");
            assertCancelled(new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.DROP, InventoryAction.DROP_ALL_SLOT), type, "drop key");

            Map<Integer, ItemStack> dragged = new LinkedHashMap<>();
            dragged.put(0, new ItemStack(org.bukkit.Material.STONE));
            InventoryDragEvent dragEvent = new InventoryDragEvent(view, new ItemStack(org.bukkit.Material.STONE), new ItemStack(org.bukkit.Material.STONE), false, dragged);
            services.menuProtectionListener().onInventoryDrag(dragEvent);
            if (!dragEvent.isCancelled()) {
                throw new IllegalStateException("GUI drag protection failed: " + type.name());
            }
        }

        Inventory mainMenu = menuSystem.createProtectedInventory(profile, stats, MenuViewType.MAIN);
        Inventory statusMenu = menuSystem.createProtectedInventory(profile, stats, MenuViewType.STATUS);
        assertOwnedHead(mainMenu.getItem(10), targetId, "main menu");
        assertOwnedHead(statusMenu.getItem(4), targetId, "status menu");
        messages.add("[EltenaCore] Menu protection verified.");
        return List.copyOf(messages);
    }

    private void assertCancelled(InventoryClickEvent event, MenuViewType type, String actionName) {
        services.menuProtectionListener().onInventoryClick(event);
        if (!event.isCancelled()) {
            throw new IllegalStateException("GUI click protection failed: " + type.name() + " / " + actionName);
        }
    }

    private void assertOwnedHead(ItemStack stack, UUID expectedOwner, String menuName) {
        if (stack == null || stack.getType() != org.bukkit.Material.PLAYER_HEAD) {
            throw new IllegalStateException("Player head missing in " + menuName);
        }
        ItemMeta itemMeta = stack.getItemMeta();
        if (!(itemMeta instanceof SkullMeta skullMeta)) {
            throw new IllegalStateException("Player head metadata invalid in " + menuName);
        }
        if (skullMeta.getOwningPlayer() == null || !expectedOwner.equals(skullMeta.getOwningPlayer().getUniqueId())) {
            throw new IllegalStateException("Player head owner mismatch in " + menuName);
        }
    }

    private InventoryView createView(Inventory top, Inventory bottom, String playerName) {
        HumanEntity human = (HumanEntity) Proxy.newProxyInstance(
            HumanEntity.class.getClassLoader(),
            new Class[]{HumanEntity.class},
            (proxy, method, args) -> {
                String name = method.getName();
                return switch (name) {
                    case "getName" -> playerName;
                    case "getInventory" -> bottom;
                    case "getEnderChest" -> bottom;
                    case "getMainHand" -> MainHand.RIGHT;
                    case "getGameMode" -> GameMode.SURVIVAL;
                    case "setWindowProperty" -> false;
                    case "getItemOnCursor", "getItemInHand", "getEquipment", "getOpenInventory", "openInventory", "openWorkbench", "openEnchanting", "openMerchant", "openAnvil", "openCartographyTable", "openGrindstone", "openLoom", "openSmithingTable", "openStonecutter", "getPotentialBedLocation", "getPotentialRespawnLocation", "getFishHook", "getBedLocation", "releaseLeftShoulderEntity", "releaseRightShoulderEntity", "getShoulderEntityLeft", "getShoulderEntityRight", "getLastDeathLocation", "fireworkBoost", "dropItem", "getLocation", "getWorld", "getUniqueId", "getServer" -> null;
                    default -> defaultValue(method.getReturnType());
                };
            }
        );

        return new InventoryView() {
            @Override
            public Inventory getTopInventory() {
                return top;
            }

            @Override
            public Inventory getBottomInventory() {
                return bottom;
            }

            @Override
            public HumanEntity getPlayer() {
                return human;
            }

            @Override
            public InventoryType getType() {
                return top.getType();
            }

            @Override
            public void setItem(int slot, ItemStack item) {
                if (slot >= 0 && slot < top.getSize()) {
                    top.setItem(slot, item);
                }
            }

            @Override
            public ItemStack getItem(int slot) {
                return slot >= 0 && slot < top.getSize() ? top.getItem(slot) : null;
            }

            @Override
            public void setCursor(ItemStack item) {
            }

            @Override
            public ItemStack getCursor() {
                return null;
            }

            @Override
            public Inventory getInventory(int rawSlot) {
                return rawSlot < top.getSize() ? top : bottom;
            }

            @Override
            public int convertSlot(int rawSlot) {
                return rawSlot < top.getSize() ? rawSlot : rawSlot - top.getSize();
            }

            @Override
            public InventoryType.SlotType getSlotType(int slot) {
                return slot < top.getSize() ? InventoryType.SlotType.CONTAINER : InventoryType.SlotType.QUICKBAR;
            }

            @Override
            public void close() {
            }

            @Override
            public int countSlots() {
                return top.getSize() + bottom.getSize();
            }

            @Override
            public boolean setProperty(Property prop, int value) {
                return false;
            }

            @Override
            public String getTitle() {
                return top.getType().getDefaultTitle();
            }

            @Override
            public String getOriginalTitle() {
                return getTitle();
            }

            @Override
            public void setTitle(String title) {
            }

        };
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        if (type == double.class) {
            return 0.0D;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}
