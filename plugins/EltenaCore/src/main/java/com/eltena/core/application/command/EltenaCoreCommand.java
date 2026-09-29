package com.eltena.core.application.command;

import com.eltena.core.application.ability.AbilityExecutionResult;
import com.eltena.core.application.progression.ProgressionResult;
import com.eltena.core.application.ui.MenuViewType;
import com.eltena.core.application.world.WorldFirstGrantResult;
import com.eltena.core.application.world.WorldProgressResult;
import com.eltena.core.application.world.WorldUniqueGrantResult;
import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.ability.AbilityDefinition;
import com.eltena.core.domain.job.JobDefinition;
import com.eltena.core.domain.player.PlayerOptionSettings;
import com.eltena.core.domain.player.PlayerProfile;
import com.eltena.core.domain.stats.PlayerStats;
import com.eltena.core.domain.title.TitleDefinition;
import com.eltena.core.domain.world.DiscoveryDefinition;
import com.eltena.core.domain.world.WorldFirstDefinition;
import com.eltena.core.domain.world.WorldRankDefinition;
import com.eltena.core.domain.world.WorldUniqueDefinition;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class EltenaCoreCommand implements TabExecutor {

    private static final List<String> ROOT_SUBCOMMANDS = List.of(
        "reload",
        "profile",
        "stats",
        "debug",
        "resetplayer",
        "exp",
        "job",
        "title",
        "menu",
        "settings",
        "status",
        "jobs",
        "titles",
        "rank",
        "discovery",
        "worldfirst",
        "unique",
        "ability",
        "mod"
    );

    private final ServiceRegistry services;

    public EltenaCoreCommand(ServiceRegistry services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        try {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "reload" -> handleReload(sender);
                case "profile" -> handleProfile(sender, args);
                case "stats" -> handleStats(sender, args);
                case "debug" -> handleDebug(sender, args);
                case "resetplayer" -> handleResetPlayer(sender, args);
                case "exp" -> handleExp(sender, args);
                case "job" -> handleJob(sender, args);
                case "title" -> handleTitle(sender, args);
                case "menu" -> handleMenu(sender, args, MenuViewType.MAIN);
                case "settings" -> handleSettings(sender, args);
                case "status" -> handleMenu(sender, args, MenuViewType.STATUS);
                case "jobs" -> handleMenu(sender, args, MenuViewType.JOBS);
                case "titles" -> handleMenu(sender, args, MenuViewType.TITLES);
                case "rank" -> handleRank(sender, args);
                case "discovery" -> handleDiscovery(sender, args);
                case "worldfirst" -> handleWorldFirst(sender, args);
                case "unique" -> handleUnique(sender, args);
                case "ability" -> handleAbility(sender, args);
                case "mod" -> handleMod(sender, args);
                default -> {
                    sender.sendMessage(color("&c不明なサブコマンドです: " + args[0]));
                    sendHelp(sender);
                    yield true;
                }
            };
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(color("&c" + exception.getMessage()));
            return true;
        } catch (IOException exception) {
            sender.sendMessage(color("&cI/O エラー: " + safeText(exception.getMessage())));
            services.plugin().getLogger().warning("[EltenaCore] command I/O failure: " + safeText(exception.getMessage()));
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterPrefix(ROOT_SUBCOMMANDS, args[0]);
        }

        String root = args[0].toLowerCase(Locale.ROOT);
        return switch (root) {
            case "debug" -> args.length == 2 ? filterPrefix(List.of("growth", "mod"), args[1]) : List.of();
            case "rank" -> args.length == 2 ? filterPrefix(List.of("status", "add", "list"), args[1]) : completePlayerOrIds(args, 2, List.of());
            case "discovery" -> tabDiscovery(args);
            case "worldfirst" -> tabWorldFirst(args);
            case "unique" -> tabUnique(args);
            case "ability" -> tabAbility(args);
            case "mod" -> tabMod(args);
            case "job" -> tabJob(args);
            case "title" -> tabTitle(args);
            case "settings" -> args.length == 2 ? onlineAndOfflinePlayerNames(args[1]) : List.of();
            case "exp", "profile", "stats", "resetplayer", "menu", "status", "jobs", "titles" -> args.length == 2 ? onlineAndOfflinePlayerNames(args[1]) : List.of();
            default -> List.of();
        };
    }

    private boolean handleReload(CommandSender sender) {
        requireAnyPermission(sender, "eltenacore.reload", "eltenacore.admin", "eltenacore.command.reload");

        services.plugin().reloadConfig();
        services.plugin().getConfig().options().copyDefaults(true);
        services.plugin().saveConfig();
        services.growthSettings().reload();
        services.messages().reload();
        services.modIntegrationSettings().reload();

        int titleCount = services.titleSystem().reloadTitles();
        int worldFirstCount = services.worldFirstService().reload();
        int uniqueCount = services.worldUniqueService().reload();
        int discoveryCount = services.discoveryService().reload();
        int skillCount = services.skillSystem().reload();
        int equipmentCount = services.equipmentSystem().reload();
        int mappingCount = services.mmoItemsDefinitionMappingRepository().reload();
        int abilityCount = services.abilitySystem().reload();
        int modChannelCount = services.modIntegrationService().reload();
        int playerStateSyncSent = services.modSyncPluginMessenger().resendPlayerStateToOnlinePlayers();
        int abilityStateSyncSent = services.modSyncPluginMessenger().resendAbilityStateToOnlinePlayers();
        int skillTreeSyncSent = services.modSyncPluginMessenger().resendSkillTreeToOnlinePlayers();

        sender.sendMessage(color("&aEltenaCore を再読込しました。"));
        sender.sendMessage(color("&7称号: &f" + titleCount));
        sender.sendMessage(color("&7WorldFirst: &f" + worldFirstCount));
        sender.sendMessage(color("&7WorldUnique: &f" + uniqueCount));
        sender.sendMessage(color("&7発見: &f" + discoveryCount));
        sender.sendMessage(color("&7スキル: &f" + skillCount));
        sender.sendMessage(color("&7装備定義: &f" + equipmentCount));
        sender.sendMessage(color("&7MMOItems マッピング: &f" + mappingCount));
        sender.sendMessage(color("&7アビリティ: &f" + abilityCount));
        sender.sendMessage(color("&7同期チャンネル: &f" + modChannelCount));
        sender.sendMessage(color("&7player_state 再送: &f" + playerStateSyncSent));
        sender.sendMessage(color("&7ability_state 再送: &f" + abilityStateSyncSent));
        sender.sendMessage(color("&7skill_tree 再送: &f" + skillTreeSyncSent));
        return true;
    }

    private boolean handleProfile(CommandSender sender, String[] args) throws IOException {
        requireAnyPermission(sender, "eltenacore.admin", "eltenacore.command.profile");
        if (args.length < 2) {
            throw new IllegalArgumentException("使い方: /eltenacore profile <player>");
        }

        OfflinePlayer target = resolveKnownPlayer(args[1]);
        PlayerProfile profile = services.playerProfiles().loadOrCreate(target.getUniqueId(), safeName(target));
        sender.sendMessage(color("&6[Profile] &f" + profile.playerName()));
        sender.sendMessage(color("&7UUID: &f" + profile.playerId()));
        sender.sendMessage(color("&7レベル: &f" + profile.level()));
        sender.sendMessage(color("&7EXP: &f" + profile.experience() + " / " + services.levelSystem().requiredExpFor(profile.level())));
        sender.sendMessage(color("&7ジョブ: &f" + services.jobSystem().displayName(profile.currentJobId())));
        sender.sendMessage(color("&7称号: &f" + services.titleSystem().displayName(profile.activeTitleId())));
        sender.sendMessage(color("&7ワールドランク: &f" + services.worldRankSystem().displayName(profile.worldRankId())));
        sender.sendMessage(color("&7ワールドランクEXP: &f" + profile.worldRankExperience()));
        sender.sendMessage(color("&7発見数: &f" + profile.discoveredExplorations().size()));
        sender.sendMessage(color("&7解放ジョブ数: &f" + profile.unlockedJobs().size()));
        sender.sendMessage(color("&7解放称号数: &f" + profile.unlockedTitles().size()));
        sender.sendMessage(color("&7解放アビリティ数: &f" + profile.unlockedAbilities().size()));
        sender.sendMessage(color("&7スキルポイント: &f" + services.skillSystem().availableSkillPoints(profile)));
        return true;
    }

    private boolean handleStats(CommandSender sender, String[] args) throws IOException {
        requireAnyPermission(sender, "eltenacore.admin", "eltenacore.command.stats");
        if (args.length < 2) {
            throw new IllegalArgumentException("使い方: /eltenacore stats <player>");
        }

        OfflinePlayer target = resolveKnownPlayer(args[1]);
        PlayerStats stats = services.playerStats().loadOrCreate(target.getUniqueId());
        sender.sendMessage(color("&6[Stats] &f" + safeName(target)));
        sender.sendMessage(color("&7HP: &f" + stats.hp() + " &8(exact " + stats.hpExact() + ")"));
        sender.sendMessage(color("&7MP: &f" + stats.mp() + " / " + stats.maxMpExact()));
        sender.sendMessage(color("&7攻撃: &f" + stats.attack()));
        sender.sendMessage(color("&7防御: &f" + stats.defense()));
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        requireAnyPermission(sender, "eltenacore.debug", "eltenacore.admin", "eltenacore.command.debug");

        if (args.length >= 2 && "growth".equalsIgnoreCase(args[1])) {
            sender.sendMessage(color("&6[Growth Debug]"));
            sender.sendMessage(color("&7level-cap: &f" + services.growthSettings().levelCap()));
            sender.sendMessage(color("&7exp-curve: &f" + services.growthSettings().expCurveFileName()));
            sender.sendMessage(color("&7exp-lines: &f" + services.growthSettings().expCurveLineCount()));
            sender.sendMessage(color("&7base-hp: &f" + services.growthSettings().baseHpExact()));
            sender.sendMessage(color("&7base-max-mp: &f" + services.growthSettings().baseMaxMpExact()));
            return true;
        }

        if (args.length >= 2 && "mod".equalsIgnoreCase(args[1])) {
            sender.sendMessage(color("&6[Addon Sync Debug]"));
            for (String line : services.modIntegrationService().debugLines()) {
                sender.sendMessage(color("&7" + line));
            }
            return true;
        }

        sender.sendMessage(color("&6[Debug]"));
        sender.sendMessage(color("&7PlaceholderAPI: &f" + yesNo(services.placeholderBridge() != null && services.placeholderBridge().isRegistered())));
        sender.sendMessage(color("&7MythicMobs: &f" + yesNo(Bukkit.getPluginManager().isPluginEnabled("MythicMobs"))));
        sender.sendMessage(color("&7MMOItems: &f" + yesNo(Bukkit.getPluginManager().isPluginEnabled("MMOItems"))));
        sender.sendMessage(color("&7Addon sync enabled: &f" + yesNo(services.modIntegrationService().isEnabled())));
        sender.sendMessage(color("&7Sync protocol: &f" + services.modIntegrationSettings().protocolVersion()));
        return true;
    }

    private boolean handleResetPlayer(CommandSender sender, String[] args) throws IOException {
        requireAnyPermission(sender, "eltenacore.admin", "eltenacore.admin.resetplayer", "eltenacore.command.resetplayer");
        if (args.length < 2) {
            throw new IllegalArgumentException("使い方: /eltenacore resetplayer <player>");
        }

        OfflinePlayer target = resolveKnownPlayer(args[1]);
        var result = services.playerResetService().reset(target);
        sender.sendMessage(color("&aプレイヤーデータを初期化しました: &f" + result.playerName()));
        sender.sendMessage(color("&7削除ファイル数: &f" + result.deletedEntries().size()));
        sender.sendMessage(color("&7同期再送: &f" + yesNo(result.synced())));
        return true;
    }

    private boolean handleExp(CommandSender sender, String[] args) throws IOException {
        requireAnyPermission(sender, "eltenacore.exp.admin", "eltenacore.admin", "eltenacore.command.exp");
        if (args.length < 3) {
            throw new IllegalArgumentException("使い方: /eltenacore exp <player> <amount>");
        }

        OfflinePlayer target = resolveKnownPlayer(args[1]);
        long amount = parseLong(args[2], "amount");
        ProgressionResult result = services.levelSystem().addExperience(target.getUniqueId(), safeName(target), amount);
        sender.sendMessage(color("&aEXP を付与しました: &f" + safeName(target) + " +" + amount));
        sendMessages(sender, result.messages());
        return true;
    }

    private boolean handleJob(CommandSender sender, String[] args) throws IOException {
        requireAnyPermission(sender, "eltenacore.admin", "eltenacore.command.job");
        if (args.length < 2) {
            throw new IllegalArgumentException("使い方: /eltenacore job <list|unlock|set> ...");
        }

        if ("list".equalsIgnoreCase(args[1])) {
            sender.sendMessage(color("&6[Jobs]"));
            for (JobDefinition definition : services.jobSystem().listJobs()) {
                sender.sendMessage(color("&7- &f" + definition.id() + " &8(" + definition.displayName() + ")"));
            }
            return true;
        }

        if (args.length < 4) {
            throw new IllegalArgumentException("使い方: /eltenacore job <unlock|set> <player> <jobId>");
        }

        OfflinePlayer target = resolveKnownPlayer(args[2]);
        if ("unlock".equalsIgnoreCase(args[1])) {
            services.jobSystem().unlockJob(target.getUniqueId(), safeName(target), args[3]);
            sender.sendMessage(color("&aジョブを解放しました: &f" + args[3]));
            return true;
        }
        if ("set".equalsIgnoreCase(args[1])) {
            services.jobSystem().setCurrentJob(target.getUniqueId(), safeName(target), args[3]);
            sender.sendMessage(color("&a現在ジョブを更新しました: &f" + args[3]));
            return true;
        }
        throw new IllegalArgumentException("使い方: /eltenacore job <list|unlock|set> ...");
    }

    private boolean handleTitle(CommandSender sender, String[] args) throws IOException {
        requireAnyPermission(sender, "eltenacore.admin", "eltenacore.command.title");
        if (args.length < 2) {
            throw new IllegalArgumentException("使い方: /eltenacore title <list|unlock|set> ...");
        }

        if ("list".equalsIgnoreCase(args[1])) {
            sender.sendMessage(color("&6[Titles]"));
            for (TitleDefinition definition : services.titleSystem().listTitles()) {
                sender.sendMessage(color("&7- &f" + definition.id() + " &8(" + definition.displayName() + ")"));
            }
            return true;
        }

        if (args.length < 4) {
            throw new IllegalArgumentException("使い方: /eltenacore title <unlock|set> <player> <titleId>");
        }

        OfflinePlayer target = resolveKnownPlayer(args[2]);
        if ("unlock".equalsIgnoreCase(args[1])) {
            services.titleSystem().unlockTitle(target.getUniqueId(), safeName(target), args[3]);
            sender.sendMessage(color("&a称号を解放しました: &f" + args[3]));
            return true;
        }
        if ("set".equalsIgnoreCase(args[1])) {
            services.titleSystem().setActiveTitle(target.getUniqueId(), safeName(target), args[3]);
            sender.sendMessage(color("&a現在称号を更新しました: &f" + args[3]));
            return true;
        }
        throw new IllegalArgumentException("使い方: /eltenacore title <list|unlock|set> ...");
    }

    private boolean handleMenu(CommandSender sender, String[] args, MenuViewType type) throws IOException {
        requireAnyPermission(sender, "eltenacore.menu.open", "eltenacore.admin", "eltenacore.command.menu");

        Player targetPlayer = null;
        OfflinePlayer targetOffline = null;
        if (args.length >= 2) {
            targetOffline = resolveKnownPlayer(args[1]);
            targetPlayer = targetOffline.getPlayer();
        } else if (sender instanceof Player player) {
            targetPlayer = player;
            targetOffline = player;
        } else {
            throw new IllegalArgumentException("コンソールからは対象プレイヤー名が必要です。");
        }

        if (targetPlayer != null && targetPlayer.isOnline()) {
            services.menuSystem().openForPlayer(targetPlayer, type);
            sender.sendMessage(color("&a" + type.title() + " を開きました: &f" + targetPlayer.getName()));
            return true;
        }

        PlayerProfile profile = services.playerProfiles().loadOrCreate(targetOffline.getUniqueId(), safeName(targetOffline));
        PlayerStats stats = services.playerStats().loadOrCreate(targetOffline.getUniqueId());
        services.menuSystem().sendConsoleSummary(sender, profile, stats, type);
        return true;
    }

    private boolean handleSettings(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            requireAnyPermission(sender, "eltenacore.admin", "eltenacore.command.settings.others");
            OfflinePlayer target = resolveKnownPlayer(args[1]);
            Player targetPlayer = target.getPlayer();
            if (targetPlayer != null && targetPlayer.isOnline()) {
                services.playerSettingsMenu().open(targetPlayer);
                sender.sendMessage(color("&aOpened settings menu: &f" + targetPlayer.getName()));
            } else {
                sendSettingsSummary(sender, target, services.playerOptionSettingsService().load(target.getUniqueId()));
            }
            return true;
        }

        requireAnyPermission(sender, "eltenacore.command.settings", "eltenacore.command.use", "eltenacore.admin");
        if (!(sender instanceof Player player)) {
            throw new IllegalArgumentException("Usage: /eltenacore settings <player>");
        }

        services.playerSettingsMenu().open(player);
        return true;
    }

    private boolean handleRank(CommandSender sender, String[] args) throws IOException {
        requireAnyPermission(sender, "eltenacore.command.world", "eltenacore.admin");
        if (args.length < 2) {
            throw new IllegalArgumentException("使い方: /eltenacore rank <status|add|list> ...");
        }

        if ("list".equalsIgnoreCase(args[1])) {
            sender.sendMessage(color("&6[World Ranks]"));
            for (WorldRankDefinition definition : services.worldRankSystem().listRanks()) {
                sender.sendMessage(color("&7- &f" + definition.id() + " &8(" + definition.displayName() + ", req=" + definition.requiredExperience() + ")"));
            }
            return true;
        }

        if ("status".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                throw new IllegalArgumentException("使い方: /eltenacore rank status <player>");
            }
            OfflinePlayer target = resolveKnownPlayer(args[2]);
            PlayerProfile profile = services.playerProfiles().loadOrCreate(target.getUniqueId(), safeName(target));
            sender.sendMessage(color("&6[World Rank] &f" + safeName(target)));
            sender.sendMessage(color("&7ランク: &f" + services.worldRankSystem().displayName(profile.worldRankId())));
            sender.sendMessage(color("&7EXP: &f" + profile.worldRankExperience()));
            return true;
        }

        if ("add".equalsIgnoreCase(args[1])) {
            if (args.length < 4) {
                throw new IllegalArgumentException("使い方: /eltenacore rank add <player> <amount>");
            }
            OfflinePlayer target = resolveKnownPlayer(args[2]);
            long amount = parseLong(args[3], "amount");
            WorldProgressResult result = services.worldRankSystem().addExperience(target.getUniqueId(), safeName(target), amount, "command");
            sender.sendMessage(color("&aワールドランクEXPを付与しました: &f" + safeName(target) + " +" + amount));
            sendMessages(sender, result.messages());
            return true;
        }

        throw new IllegalArgumentException("使い方: /eltenacore rank <status|add|list> ...");
    }

    private boolean handleDiscovery(CommandSender sender, String[] args) throws IOException {
        requireAnyPermission(sender, "eltenacore.command.world", "eltenacore.admin");
        if (args.length < 2) {
            throw new IllegalArgumentException("使い方: /eltenacore discovery <list|grant> ...");
        }

        if ("list".equalsIgnoreCase(args[1])) {
            sender.sendMessage(color("&6[Discoveries]"));
            for (DiscoveryDefinition definition : services.discoveryService().listDefinitions()) {
                sender.sendMessage(color("&7- &f" + definition.id() + " &8(" + definition.displayName() + ")"));
            }
            return true;
        }

        if ("grant".equalsIgnoreCase(args[1])) {
            if (args.length < 4) {
                throw new IllegalArgumentException("使い方: /eltenacore discovery grant <player> <id>");
            }
            OfflinePlayer target = resolveKnownPlayer(args[2]);
            WorldProgressResult result = services.discoveryService().grant(target.getUniqueId(), safeName(target), args[3]);
            sender.sendMessage(color("&a発見を付与しました: &f" + args[3]));
            sendMessages(sender, result.messages());
            return true;
        }

        throw new IllegalArgumentException("使い方: /eltenacore discovery <list|grant> ...");
    }

    private boolean handleWorldFirst(CommandSender sender, String[] args) throws IOException {
        requireAnyPermission(sender, "eltenacore.command.world", "eltenacore.admin");
        if (args.length < 2) {
            throw new IllegalArgumentException("使い方: /eltenacore worldfirst <list|check|grant> ...");
        }

        if ("list".equalsIgnoreCase(args[1])) {
            sender.sendMessage(color("&6[World First]"));
            for (WorldFirstDefinition definition : services.worldFirstService().listDefinitions()) {
                sender.sendMessage(color("&7- &f" + definition.id() + " &8(" + definition.displayName() + ")"));
            }
            return true;
        }

        if ("check".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                throw new IllegalArgumentException("使い方: /eltenacore worldfirst check <id>");
            }
            var record = services.worldFirstService().check(args[2]);
            if (record == null) {
                sender.sendMessage(color("&7未取得です: &f" + args[2]));
            } else {
                sender.sendMessage(color("&a取得済み: &f" + record.playerName() + " &7at &f" + record.timestamp()));
            }
            return true;
        }

        if ("grant".equalsIgnoreCase(args[1])) {
            if (args.length < 4) {
                throw new IllegalArgumentException("使い方: /eltenacore worldfirst grant <player> <id>");
            }
            OfflinePlayer target = resolveKnownPlayer(args[2]);
            WorldFirstGrantResult result = services.worldFirstService().grant(target.getUniqueId(), safeName(target), args[3]);
            sender.sendMessage(color("&aWorldFirst を処理しました: &f" + args[3] + " &7(granted=" + yesNo(result.granted()) + ")"));
            sendMessages(sender, result.messages());
            return true;
        }

        throw new IllegalArgumentException("使い方: /eltenacore worldfirst <list|check|grant> ...");
    }

    private boolean handleUnique(CommandSender sender, String[] args) throws IOException {
        requireAnyPermission(sender, "eltenacore.command.world", "eltenacore.admin");
        if (args.length < 2) {
            throw new IllegalArgumentException("使い方: /eltenacore unique <list|check|grant> ...");
        }

        if ("list".equalsIgnoreCase(args[1])) {
            sender.sendMessage(color("&6[World Unique]"));
            for (WorldUniqueDefinition definition : services.worldUniqueService().listDefinitions()) {
                sender.sendMessage(color("&7- &f" + definition.id() + " &8(" + definition.displayName() + ")"));
            }
            return true;
        }

        if ("check".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                throw new IllegalArgumentException("使い方: /eltenacore unique check <id>");
            }
            var record = services.worldUniqueService().check(args[2]);
            if (record == null) {
                sender.sendMessage(color("&7未取得です: &f" + args[2]));
            } else {
                sender.sendMessage(color("&a取得済み: &f" + record.playerName() + " &7at &f" + record.timestamp()));
            }
            return true;
        }

        if ("grant".equalsIgnoreCase(args[1])) {
            if (args.length < 4) {
                throw new IllegalArgumentException("使い方: /eltenacore unique grant <player> <id>");
            }
            OfflinePlayer target = resolveKnownPlayer(args[2]);
            WorldUniqueGrantResult result = services.worldUniqueService().grant(target.getUniqueId(), safeName(target), args[3]);
            sender.sendMessage(color("&aWorldUnique を処理しました: &f" + args[3] + " &7(granted=" + yesNo(result.granted()) + ")"));
            sendMessages(sender, result.messages());
            return true;
        }

        throw new IllegalArgumentException("使い方: /eltenacore unique <list|check|grant> ...");
    }

    private boolean handleAbility(CommandSender sender, String[] args) throws IOException {
        requireAnyPermission(sender, "eltenacore.command.ability", "eltenacore.admin");
        if (args.length < 2) {
            throw new IllegalArgumentException("使い方: /eltenacore ability <list|use|sync> ...");
        }

        if ("list".equalsIgnoreCase(args[1])) {
            sender.sendMessage(color("&6[Abilities]"));
            for (AbilityDefinition definition : services.abilitySystem().listDefinitions()) {
                sender.sendMessage(color("&7- &f" + definition.id() + " &8(" + definition.displayName() + ")"));
            }
            return true;
        }

        if ("sync".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                throw new IllegalArgumentException("使い方: /eltenacore ability sync <player>");
            }
            Player target = requireOnlinePlayer(args[2]);
            boolean playerStateSent = services.modSyncPluginMessenger().sendPlayerState(target);
            boolean abilityStateSent = services.modSyncPluginMessenger().sendAbilityState(target);
            sender.sendMessage(color("&aアビリティ同期を再送しました: &f" + target.getName()));
            sender.sendMessage(color("&7player_state: &f" + yesNo(playerStateSent)));
            sender.sendMessage(color("&7ability_state: &f" + yesNo(abilityStateSent)));
            return true;
        }

        if ("use".equalsIgnoreCase(args[1])) {
            if (args.length < 4) {
                throw new IllegalArgumentException("使い方: /eltenacore ability use <player> <abilityId>");
            }
            Player target = requireOnlinePlayer(args[2]);
            AbilityExecutionResult result = services.abilitySystem().useAbility(target, args[3]);
            sender.sendMessage(color("&aアビリティ実行: &f" + result.abilityId() + " &7(success=" + yesNo(result.executed()) + ")"));
            if (result.denied()) {
                sender.sendMessage(color("&7reason: &f" + result.denialReason()));
            }
            sendMessages(sender, result.messages());
            return true;
        }

        throw new IllegalArgumentException("使い方: /eltenacore ability <list|use|sync> ...");
    }

    private boolean handleMod(CommandSender sender, String[] args) {
        requireAnyPermission(sender, "eltenacore.command.mod", "eltenacore.admin");
        if (args.length < 2) {
            throw new IllegalArgumentException("使い方: /eltenacore mod <status|debug|reload|sync> ...");
        }

        if ("status".equalsIgnoreCase(args[1])) {
            sender.sendMessage(color("&6[Addon Sync Status]"));
            for (String line : services.modIntegrationService().statusLines()) {
                sender.sendMessage(color("&7" + line));
            }
            return true;
        }

        if ("debug".equalsIgnoreCase(args[1])) {
            sender.sendMessage(color("&6[Addon Sync Debug]"));
            for (String line : services.modIntegrationService().debugLines()) {
                sender.sendMessage(color("&7" + line));
            }
            return true;
        }

        if ("reload".equalsIgnoreCase(args[1])) {
            int count = services.modIntegrationService().reload();
            sender.sendMessage(color("&aAddon Sync 設定を再読込しました。"));
            sender.sendMessage(color("&7チャンネル数: &f" + count));
            return true;
        }

        if ("sync".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                throw new IllegalArgumentException("使い方: /eltenacore mod sync <player>");
            }
            Player target = requireOnlinePlayer(args[2]);
            boolean playerStateSent = services.modSyncPluginMessenger().sendPlayerState(target);
            boolean abilityStateSent = services.modSyncPluginMessenger().sendAbilityState(target);
            boolean skillTreeSent = services.modSyncPluginMessenger().sendSkillTree(target);
            sender.sendMessage(color("&a同期を再送しました: &f" + target.getName()));
            sender.sendMessage(color("&7player_state: &f" + yesNo(playerStateSent)));
            sender.sendMessage(color("&7ability_state: &f" + yesNo(abilityStateSent)));
            sender.sendMessage(color("&7skill_tree: &f" + yesNo(skillTreeSent)));
            return true;
        }

        throw new IllegalArgumentException("使い方: /eltenacore mod <status|debug|reload|sync> ...");
    }

    private List<String> tabDiscovery(String[] args) {
        if (args.length == 2) {
            return filterPrefix(List.of("list", "grant"), args[1]);
        }
        if (args.length == 3 && "grant".equalsIgnoreCase(args[1])) {
            return onlineAndOfflinePlayerNames(args[2]);
        }
        if (args.length == 4 && "grant".equalsIgnoreCase(args[1])) {
            return filterPrefix(services.discoveryService().listDefinitions().stream().map(DiscoveryDefinition::id).toList(), args[3]);
        }
        return List.of();
    }

    private List<String> tabWorldFirst(String[] args) {
        if (args.length == 2) {
            return filterPrefix(List.of("list", "check", "grant"), args[1]);
        }
        if (args.length == 3 && "grant".equalsIgnoreCase(args[1])) {
            return onlineAndOfflinePlayerNames(args[2]);
        }
        if ((args.length == 3 && "check".equalsIgnoreCase(args[1])) || (args.length == 4 && "grant".equalsIgnoreCase(args[1]))) {
            int index = "grant".equalsIgnoreCase(args[1]) ? 3 : 2;
            return filterPrefix(services.worldFirstService().listDefinitions().stream().map(WorldFirstDefinition::id).toList(), args[index]);
        }
        return List.of();
    }

    private List<String> tabUnique(String[] args) {
        if (args.length == 2) {
            return filterPrefix(List.of("list", "check", "grant"), args[1]);
        }
        if (args.length == 3 && "grant".equalsIgnoreCase(args[1])) {
            return onlineAndOfflinePlayerNames(args[2]);
        }
        if ((args.length == 3 && "check".equalsIgnoreCase(args[1])) || (args.length == 4 && "grant".equalsIgnoreCase(args[1]))) {
            int index = "grant".equalsIgnoreCase(args[1]) ? 3 : 2;
            return filterPrefix(services.worldUniqueService().listDefinitions().stream().map(WorldUniqueDefinition::id).toList(), args[index]);
        }
        return List.of();
    }

    private List<String> tabAbility(String[] args) {
        if (args.length == 2) {
            return filterPrefix(List.of("list", "use", "sync"), args[1]);
        }
        if ((args.length == 3 && "sync".equalsIgnoreCase(args[1])) || (args.length == 3 && "use".equalsIgnoreCase(args[1]))) {
            return onlinePlayerNames(args[2]);
        }
        if (args.length == 4 && "use".equalsIgnoreCase(args[1])) {
            return filterPrefix(services.abilitySystem().listDefinitions().stream().map(AbilityDefinition::id).toList(), args[3]);
        }
        return List.of();
    }

    private List<String> tabMod(String[] args) {
        if (args.length == 2) {
            return filterPrefix(List.of("status", "debug", "reload", "sync"), args[1]);
        }
        if (args.length == 3 && "sync".equalsIgnoreCase(args[1])) {
            return onlinePlayerNames(args[2]);
        }
        return List.of();
    }

    private List<String> tabJob(String[] args) {
        if (args.length == 2) {
            return filterPrefix(List.of("list", "unlock", "set"), args[1]);
        }
        if ((args.length == 3 && "unlock".equalsIgnoreCase(args[1])) || (args.length == 3 && "set".equalsIgnoreCase(args[1]))) {
            return onlineAndOfflinePlayerNames(args[2]);
        }
        if (args.length == 4 && ("unlock".equalsIgnoreCase(args[1]) || "set".equalsIgnoreCase(args[1]))) {
            return filterPrefix(services.jobSystem().listJobs().stream().map(JobDefinition::id).toList(), args[3]);
        }
        return List.of();
    }

    private List<String> tabTitle(String[] args) {
        if (args.length == 2) {
            return filterPrefix(List.of("list", "unlock", "set"), args[1]);
        }
        if ((args.length == 3 && "unlock".equalsIgnoreCase(args[1])) || (args.length == 3 && "set".equalsIgnoreCase(args[1]))) {
            return onlineAndOfflinePlayerNames(args[2]);
        }
        if (args.length == 4 && ("unlock".equalsIgnoreCase(args[1]) || "set".equalsIgnoreCase(args[1]))) {
            return filterPrefix(services.titleSystem().listTitles().stream().map(TitleDefinition::id).toList(), args[3]);
        }
        return List.of();
    }

    private List<String> completePlayerOrIds(String[] args, int playerIndex, List<String> ids) {
        if (args.length == playerIndex + 1) {
            return onlineAndOfflinePlayerNames(args[playerIndex]);
        }
        return ids;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(color("&6EltenaCore コマンド"));
        sender.sendMessage(color("&7/eltenacore reload"));
        sender.sendMessage(color("&7/eltenacore profile <player>"));
        sender.sendMessage(color("&7/eltenacore stats <player>"));
        sender.sendMessage(color("&7/eltenacore debug [growth|mod]"));
        sender.sendMessage(color("&7/eltenacore resetplayer <player>"));
        sender.sendMessage(color("&7/eltenacore exp <player> <amount>"));
        sender.sendMessage(color("&7/eltenacore job <list|unlock|set> ..."));
        sender.sendMessage(color("&7/eltenacore title <list|unlock|set> ..."));
        sender.sendMessage(color("&7/eltenacore settings [player]"));
        sender.sendMessage(color("&7/eltenacore menu|status|jobs|titles [player]"));
        sender.sendMessage(color("&7/eltenacore rank <status|add|list> ..."));
        sender.sendMessage(color("&7/eltenacore discovery <list|grant> ..."));
        sender.sendMessage(color("&7/eltenacore worldfirst <list|check|grant> ..."));
        sender.sendMessage(color("&7/eltenacore unique <list|check|grant> ..."));
        sender.sendMessage(color("&7/eltenacore ability <list|use|sync> ..."));
        sender.sendMessage(color("&7/eltenacore mod <status|debug|reload|sync> ..."));
    }

    private void sendMessages(CommandSender sender, List<String> messages) {
        if (messages == null) {
            return;
        }
        for (String line : messages) {
            if (line == null || line.isBlank()) {
                continue;
            }
            sender.sendMessage(color("&7" + line));
        }
    }

    private void sendSettingsSummary(CommandSender sender, OfflinePlayer target, PlayerOptionSettings settings) {
        sender.sendMessage(color("&6[Settings] &f" + safeName(target)));
        sender.sendMessage(color("&7" + services.messages().get("settings.option.floating-damage") + ": &f" + settingsStatusText(settings.floatingDamage())));
        sender.sendMessage(color("&7" + services.messages().get("settings.option.chat-damage") + ": &f" + settingsStatusText(settings.chatDamage())));
        sender.sendMessage(color("&7" + services.messages().get("settings.option.exp-chat") + ": &f" + settingsStatusText(settings.expChat())));
        sender.sendMessage(color("&7" + services.messages().get("settings.option.dps-chat") + ": &f" + settingsStatusText(settings.dpsChat())));
    }

    private OfflinePlayer resolveKnownPlayer(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(input);
        if (!offline.isOnline() && !offline.hasPlayedBefore() && offline.getName() == null) {
            throw new IllegalArgumentException("プレイヤーが見つかりません: " + input);
        }
        return offline;
    }

    private Player requireOnlinePlayer(String input) {
        Player player = Bukkit.getPlayerExact(input);
        if (player == null || !player.isOnline()) {
            throw new IllegalArgumentException("オンラインのプレイヤーが必要です: " + input);
        }
        return player;
    }

    private String safeName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private void requireAnyPermission(CommandSender sender, String... permissions) {
        for (String permission : permissions) {
            if (permission != null && !permission.isBlank() && sender.hasPermission(permission)) {
                return;
            }
        }
        throw new IllegalArgumentException("権限がありません。");
    }

    private long parseLong(String raw, String name) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("数値が不正です (" + name + "): " + raw);
        }
    }

    private List<String> filterPrefix(List<String> values, String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                matches.add(value);
            }
        }
        return matches;
    }

    private List<String> onlinePlayerNames(String prefix) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return filterPrefix(names, prefix);
    }

    private List<String> onlineAndOfflinePlayerNames(String prefix) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() != null && !names.contains(player.getName())) {
                names.add(player.getName());
            }
        }
        return filterPrefix(names, prefix);
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private String settingsStatusText(boolean enabled) {
        return services.messages().get(enabled ? "settings.status.enabled" : "settings.status.disabled");
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }
}
