package com.eltena.core.application.ui;

import com.eltena.core.application.job.JobSystem;
import com.eltena.core.application.title.TitleSystem;
import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.job.JobDefinition;
import com.eltena.core.domain.player.PlayerProfile;
import com.eltena.core.domain.stats.PlayerStats;
import com.eltena.core.domain.title.TitleDefinition;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class MenuSystem {

    private final ServiceRegistry services;
    private final JobSystem jobSystem;
    private final TitleSystem titleSystem;

    public MenuSystem(ServiceRegistry services, JobSystem jobSystem, TitleSystem titleSystem) {
        this.services = Objects.requireNonNull(services, "services");
        this.jobSystem = Objects.requireNonNull(jobSystem, "jobSystem");
        this.titleSystem = Objects.requireNonNull(titleSystem, "titleSystem");
    }

    public void openForPlayer(Player player, MenuViewType type) throws IOException {
        PlayerProfile profile = services.playerProfiles().loadOrCreate(player.getUniqueId(), player.getName());
        PlayerStats stats = services.playerStats().loadOrCreate(player.getUniqueId());
        player.openInventory(createProtectedInventory(profile, stats, type));
    }

    public Inventory createProtectedInventory(PlayerProfile profile, PlayerStats stats, MenuViewType type) {
        return buildInventory(profile, stats, type);
    }

    public boolean isProtectedView(InventoryView view) {
        return view != null
            && view.getTopInventory() != null
            && view.getTopInventory().getHolder() instanceof EltenaMenuHolder;
    }

    public void sendConsoleSummary(CommandSender sender, PlayerProfile profile, PlayerStats stats, MenuViewType type) {
        sender.sendMessage(color("&6[EltenaCore] " + type.title() + " - " + profile.playerName()));
        switch (type) {
            case MAIN -> {
                sender.sendMessage(color("&7レベル: &f" + profile.level()));
                sender.sendMessage(color("&7EXP: &f" + profile.experience()));
                sender.sendMessage(color("&7ジョブ: &f" + jobSystem.displayName(profile.currentJobId())));
                sender.sendMessage(color("&7称号: &f" + titleSystem.displayName(profile.activeTitleId())));
                sender.sendMessage(color("&7ワールドランク: &f" + services.worldRankSystem().displayName(profile.worldRankId())));
                sender.sendMessage(color("&7スキルポイント: &f" + services.skillSystem().availableSkillPoints(profile)));
            }
            case STATUS -> {
                sender.sendMessage(color("&7HP: &f" + stats.hp()));
                sender.sendMessage(color("&7MP: &f" + stats.mp()));
                sender.sendMessage(color("&7攻撃: &f" + stats.attack()));
                sender.sendMessage(color("&7防御: &f" + stats.defense()));
            }
            case JOBS -> {
                sender.sendMessage(color("&7現在ジョブ: &f" + jobSystem.displayName(profile.currentJobId())));
                sender.sendMessage(color("&7解放数: &f" + profile.unlockedJobs().size()));
            }
            case TITLES -> {
                sender.sendMessage(color("&7現在称号: &f" + titleSystem.displayName(profile.activeTitleId())));
                sender.sendMessage(color("&7解放数: &f" + profile.unlockedTitles().size()));
            }
        }
    }

    private Inventory buildInventory(PlayerProfile profile, PlayerStats stats, MenuViewType type) {
        int size = switch (type) {
            case JOBS, TITLES -> 54;
            default -> 27;
        };

        EltenaMenuHolder holder = new EltenaMenuHolder(type);
        Inventory inventory = Bukkit.createInventory(holder, size, type.title());
        holder.bind(inventory);

        switch (type) {
            case MAIN -> populateMain(inventory, profile);
            case STATUS -> populateStatus(inventory, profile, stats);
            case JOBS -> populateJobs(inventory, profile);
            case TITLES -> populateTitles(inventory, profile);
        }
        return inventory;
    }

    private void populateMain(Inventory inventory, PlayerProfile profile) {
        inventory.setItem(10, playerHead(resolveOwner(profile), "&eプロフィール", List.of(
            line("プレイヤー", profile.playerName()),
            line("レベル", profile.level()),
            line("ジョブ", jobSystem.displayName(profile.currentJobId()))
        )));
        inventory.setItem(12, item(Material.IRON_SWORD, "&6ジョブ", List.of(
            line("現在", jobSystem.displayName(profile.currentJobId())),
            line("解放数", profile.unlockedJobs().size())
        )));
        inventory.setItem(14, item(Material.NAME_TAG, "&d称号", List.of(
            line("現在", titleSystem.displayName(profile.activeTitleId())),
            line("解放数", profile.unlockedTitles().size())
        )));
        inventory.setItem(16, item(Material.EXPERIENCE_BOTTLE, "&b進行状況", List.of(
            line("ワールドランク", services.worldRankSystem().displayName(profile.worldRankId())),
            line("ランクEXP", profile.worldRankExperience()),
            line("スキルポイント", services.skillSystem().availableSkillPoints(profile))
        )));
        inventory.setItem(21, item(Material.COMPASS, "&a発見", List.of(
            line("発見数", profile.discoveryCount()),
            line("登録数", profile.discoveredExplorations().size())
        )));
        inventory.setItem(23, item(Material.BOOK, "&f概要", List.of(
            line("アビリティ解放", profile.unlockedAbilities().size()),
            line("装備スロット使用", usedEquippedSlots(profile))
        )));
    }

    private void populateStatus(Inventory inventory, PlayerProfile profile, PlayerStats stats) {
        inventory.setItem(4, playerHead(resolveOwner(profile), "&eステータス", List.of(
            line("プレイヤー", profile.playerName()),
            line("ワールドランク", services.worldRankSystem().displayName(profile.worldRankId())),
            line("称号", titleSystem.displayName(profile.activeTitleId()))
        )));
        inventory.setItem(10, item(Material.REDSTONE, "&cHP", List.of(line("現在値", stats.hp()))));
        inventory.setItem(11, item(Material.LAPIS_LAZULI, "&9MP", List.of(line("現在値", stats.mp()))));
        inventory.setItem(13, item(Material.IRON_SWORD, "&6攻撃", List.of(line("現在値", stats.attack()))));
        inventory.setItem(15, item(Material.SHIELD, "&7防御", List.of(line("現在値", stats.defense()))));
        inventory.setItem(16, item(Material.EXPERIENCE_BOTTLE, "&a成長", List.of(
            line("レベル", profile.level()),
            line("EXP", profile.experience())
        )));
    }

    private void populateJobs(Inventory inventory, PlayerProfile profile) {
        int slot = 0;
        for (JobDefinition definition : jobSystem.listJobs()) {
            if (slot >= inventory.getSize()) {
                break;
            }
            inventory.setItem(slot++, item(Material.IRON_SWORD, definition.displayName(), List.of(
                definition.description(),
                line("Tier", definition.tier().displayName()),
                line("解放済み", yesNo(profile.unlockedJobs().contains(definition.id()))),
                line("現在選択", yesNo(profile.currentJobId().equals(definition.id())))
            )));
        }
    }

    private void populateTitles(Inventory inventory, PlayerProfile profile) {
        int slot = 0;
        for (TitleDefinition definition : titleSystem.listTitles()) {
            if (slot >= inventory.getSize()) {
                break;
            }
            inventory.setItem(slot++, item(Material.NAME_TAG, definition.displayName(), List.of(
                definition.description(),
                line("カテゴリ", definition.category().displayName()),
                line("解放済み", yesNo(profile.unlockedTitles().contains(definition.id()))),
                line("現在選択", yesNo(profile.activeTitleId().equals(definition.id())))
            )));
        }
    }

    private ItemStack item(Material material, String title, List<String> loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(color(title));
        List<String> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(color(line));
        }
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack playerHead(OfflinePlayer owner, String title, List<String> loreLines) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta rawMeta = stack.getItemMeta();
        if (!(rawMeta instanceof SkullMeta meta)) {
            return item(Material.PLAYER_HEAD, title, loreLines);
        }
        meta.setOwningPlayer(owner);
        meta.setDisplayName(color(title));
        List<String> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(color(line));
        }
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private OfflinePlayer resolveOwner(PlayerProfile profile) {
        UUID playerId = profile == null ? null : profile.playerId();
        if (playerId == null) {
            return Bukkit.getOfflinePlayer("Unknown");
        }
        return Bukkit.getOfflinePlayer(playerId);
    }

    private int usedEquippedSlots(PlayerProfile profile) {
        int count = 0;
        for (String value : profile.equippedAbilities().values()) {
            if (value != null && !value.isBlank()) {
                count++;
            }
        }
        return count;
    }

    private String line(String label, Object value) {
        return "&7" + label + ": &f" + value;
    }

    private String yesNo(boolean value) {
        return value ? "はい" : "いいえ";
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }
}
