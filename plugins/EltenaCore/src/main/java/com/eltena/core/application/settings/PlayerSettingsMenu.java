package com.eltena.core.application.settings;

import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.player.PlayerOptionSettings;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PlayerSettingsMenu {

    public static final int SLOT_FLOATING_DAMAGE = 10;
    public static final int SLOT_CHAT_DAMAGE = 12;
    public static final int SLOT_EXP_CHAT = 14;
    public static final int SLOT_DPS_CHAT = 16;

    private final ServiceRegistry services;

    public PlayerSettingsMenu(ServiceRegistry services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    public void open(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.openInventory(buildInventory(player.getUniqueId()));
    }

    public Inventory buildInventory(UUID playerId) {
        PlayerSettingsMenuHolder holder = new PlayerSettingsMenuHolder(playerId);
        Inventory inventory = Bukkit.createInventory(holder, 27, title());
        holder.bind(inventory);
        render(inventory, playerId);
        return inventory;
    }

    public void render(Inventory inventory, UUID playerId) {
        if (inventory == null) {
            return;
        }
        PlayerOptionSettings settings = services.playerOptionSettingsService().load(playerId);
        inventory.clear();
        inventory.setItem(SLOT_FLOATING_DAMAGE, optionItem(
            Material.REDSTONE,
            services.messages().get("settings.option.floating-damage"),
            services.messages().get("settings.option.floating-damage-desc"),
            settings.floatingDamage()
        ));
        inventory.setItem(SLOT_CHAT_DAMAGE, optionItem(
            Material.IRON_SWORD,
            services.messages().get("settings.option.chat-damage"),
            services.messages().get("settings.option.chat-damage-desc"),
            settings.chatDamage()
        ));
        inventory.setItem(SLOT_EXP_CHAT, optionItem(
            Material.EXPERIENCE_BOTTLE,
            services.messages().get("settings.option.exp-chat"),
            services.messages().get("settings.option.exp-chat-desc"),
            settings.expChat()
        ));
        inventory.setItem(SLOT_DPS_CHAT, optionItem(
            Material.PAPER,
            services.messages().get("settings.option.dps-chat"),
            services.messages().get("settings.option.dps-chat-desc"),
            settings.dpsChat()
        ));
    }

    public boolean isSettingsView(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof PlayerSettingsMenuHolder;
    }

    public UUID ownerId(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof PlayerSettingsMenuHolder holder
            ? holder.playerId()
            : null;
    }

    public String title() {
        return color(services.messages().get("settings.title"));
    }

    private ItemStack optionItem(Material material, String name, String description, boolean enabled) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(color("&e" + name));
        List<String> lore = new ArrayList<>();
        lore.add(color("&7" + description));
        lore.add(color("&7" + services.messages().get("settings.status-label") + ": &f" + statusText(enabled)));
        lore.add(color("&8" + services.messages().get("settings.click-toggle")));
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private String statusText(boolean enabled) {
        return services.messages().get(enabled ? "settings.status.enabled" : "settings.status.disabled");
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
