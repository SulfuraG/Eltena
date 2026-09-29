package com.eltena.core.application.settings;

import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.player.PlayerOptionSettings;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.Objects;
import java.util.UUID;

public final class PlayerSettingsMenuListener implements Listener {

    private final ServiceRegistry services;
    private final PlayerSettingsMenu menu;

    public PlayerSettingsMenuListener(ServiceRegistry services, PlayerSettingsMenu menu) {
        this.services = Objects.requireNonNull(services, "services");
        this.menu = Objects.requireNonNull(menu, "menu");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!menu.isSettingsView(top)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }

        UUID ownerId = menu.ownerId(top);
        if (ownerId == null || !ownerId.equals(player.getUniqueId())) {
            return;
        }

        PlayerOptionSettings settings = switch (event.getRawSlot()) {
            case PlayerSettingsMenu.SLOT_FLOATING_DAMAGE -> services.playerOptionSettingsService().toggleFloatingDamage(ownerId);
            case PlayerSettingsMenu.SLOT_CHAT_DAMAGE -> services.playerOptionSettingsService().toggleChatDamage(ownerId);
            case PlayerSettingsMenu.SLOT_EXP_CHAT -> services.playerOptionSettingsService().toggleExpChat(ownerId);
            case PlayerSettingsMenu.SLOT_DPS_CHAT -> services.playerOptionSettingsService().toggleDpsChat(ownerId);
            default -> null;
        };
        if (settings == null) {
            return;
        }
        menu.render(top, ownerId);
        player.sendMessage(color(toggleMessage(event.getRawSlot(), settings)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (menu.isSettingsView(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    private String toggleMessage(int slot, PlayerOptionSettings settings) {
        return switch (slot) {
            case PlayerSettingsMenu.SLOT_FLOATING_DAMAGE -> services.messages().get(
                "settings.toggle",
                "name", services.messages().get("settings.option.floating-damage"),
                "value", statusText(settings.floatingDamage())
            );
            case PlayerSettingsMenu.SLOT_CHAT_DAMAGE -> services.messages().get(
                "settings.toggle",
                "name", services.messages().get("settings.option.chat-damage"),
                "value", statusText(settings.chatDamage())
            );
            case PlayerSettingsMenu.SLOT_EXP_CHAT -> services.messages().get(
                "settings.toggle",
                "name", services.messages().get("settings.option.exp-chat"),
                "value", statusText(settings.expChat())
            );
            case PlayerSettingsMenu.SLOT_DPS_CHAT -> services.messages().get(
                "settings.toggle",
                "name", services.messages().get("settings.option.dps-chat"),
                "value", statusText(settings.dpsChat())
            );
            default -> "";
        };
    }

    private String statusText(boolean enabled) {
        return services.messages().get(enabled ? "settings.status.enabled" : "settings.status.disabled");
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
