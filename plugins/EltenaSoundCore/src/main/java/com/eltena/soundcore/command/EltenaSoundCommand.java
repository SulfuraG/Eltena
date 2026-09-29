package com.eltena.soundcore.command;

import com.eltena.soundcore.EltenaSoundCorePlugin;
import com.eltena.soundcore.live.LiveAssetDefinition;
import com.eltena.soundcore.live.LiveAssetDispatchResult;
import com.eltena.soundcore.playback.PlayResult;
import com.eltena.soundcore.playback.SoundPlaybackService;
import com.eltena.soundcore.playback.StopResult;
import com.eltena.soundcore.sound.SoundCategory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class EltenaSoundCommand implements CommandExecutor, TabCompleter {
    private final EltenaSoundCorePlugin plugin;

    public EltenaSoundCommand(EltenaSoundCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
        CommandSender sender,
        Command command,
        String label,
        String[] args
    ) {
        if (!sender.hasPermission("eltenasound.command.use")) {
            plugin.sendPrefixed(sender, "command.error.no-permission", Map.of());
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();
        try {
            return switch (subcommand) {
                case "reload" -> handleReload(sender);
                case "play" -> handlePlay(sender, args);
                case "stop" -> handleStop(sender, args);
                case "live" -> handleLive(sender, args);
                default -> {
                    plugin.sendPrefixed(sender, "command.error.unknown-subcommand", Map.of());
                    sendHelp(sender);
                    yield true;
                }
            };
        } catch (IllegalArgumentException exception) {
            String message = exception.getMessage() == null ? "" : exception.getMessage();
            if (message.startsWith("sound:")) {
                String soundId = message.substring("sound:".length());
                if (plugin.isDebugLogEnabled()) {
                    plugin.logWarning("log.unknown-sound-id", Map.of("soundId", soundId));
                }
                plugin.sendPrefixed(
                    sender,
                    "command.error.unknown-sound",
                    Map.of("soundId", soundId)
                );
            } else if (message.startsWith("category:")) {
                String category = message.substring("category:".length());
                if (plugin.isDebugLogEnabled()) {
                    plugin.logWarning("log.unknown-category", Map.of("category", category));
                }
                plugin.sendPrefixed(
                    sender,
                    "command.error.unknown-category",
                    Map.of("category", category)
                );
            } else if (message.startsWith("number:")) {
                plugin.sendPrefixed(sender, "command.error.invalid-number", Map.of());
            } else if (message.startsWith("live:")) {
                String assetId = message.substring("live:".length());
                plugin.sendPrefixed(
                    sender,
                    "command.live.error.unknown-asset",
                    Map.of("assetId", assetId)
                );
            } else if (message.startsWith("usage")) {
                sendHelp(sender);
            } else {
                plugin.sendPrefixed(sender, "command.error.internal", Map.of());
            }
            return true;
        } catch (RuntimeException exception) {
            plugin.sendPrefixed(sender, "command.error.internal", Map.of());
            throw exception;
        }
    }

    @Override
    public List<String> onTabComplete(
        CommandSender sender,
        Command command,
        String alias,
        String[] args
    ) {
        if (args.length == 1) {
            return filter(List.of("reload", "play", "stop", "live"), args[0]);
        }
        if (args.length == 2 && "play".equalsIgnoreCase(args[0])) {
            return filter(new ArrayList<>(plugin.soundRegistry().soundIds()), args[1]);
        }
        if (args.length == 2 && "stop".equalsIgnoreCase(args[0])) {
            return filter(
                List.of(SoundCategory.BGM.name(), SoundCategory.SYSTEM.name(), SoundCategory.VOICE.name()),
                args[1]
            );
        }
        if (args.length == 2 && "live".equalsIgnoreCase(args[0])) {
            return filter(List.of("list", "reload", "play", "send-manifest", "send"), args[1]);
        }
        if (args.length == 3 && "live".equalsIgnoreCase(args[0]) && "play".equalsIgnoreCase(args[1])) {
            return filter(new ArrayList<>(plugin.liveAssetRegistry().liveAssetIds()), args[2]);
        }
        if (args.length == 3 && "live".equalsIgnoreCase(args[0]) && "send-manifest".equalsIgnoreCase(args[1])) {
            return filter(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        if (args.length == 3 && "live".equalsIgnoreCase(args[0]) && "send".equalsIgnoreCase(args[1])) {
            return filter(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        if (args.length == 4 && "live".equalsIgnoreCase(args[0]) && "send".equalsIgnoreCase(args[1])) {
            return filter(new ArrayList<>(plugin.liveAssetRegistry().liveAssetIds()), args[3]);
        }
        return Collections.emptyList();
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("eltenasound.command.reload")) {
            plugin.sendPrefixed(sender, "command.error.no-permission", Map.of());
            return true;
        }
        plugin.reloadPluginState();
        plugin.sendPrefixed(
            sender,
            "command.reload.success",
            Map.of(
                "sounds", Integer.toString(plugin.soundRegistry().count()),
                "liveAssets", Integer.toString(plugin.liveAssetRegistry().count())
            )
        );
        plugin.logInfo("log.reload", plugin.runtimeStatePlaceholders());
        return true;
    }

    private boolean handlePlay(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eltenasound.command.play")) {
            plugin.sendPrefixed(sender, "command.error.no-permission", Map.of());
            return true;
        }
        if (args.length != 2) {
            throw new IllegalArgumentException("usage");
        }

        String soundId = args[1];
        PlayResult result;
        try {
            result = plugin.playbackService().play(soundId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("sound:" + soundId, exception);
        }

        if (result.targetCount() == 0) {
            if (result.notReadyCount() > 0) {
                plugin.sendPrefixed(sender, "command.error.player-not-ready", Map.of());
                return true;
            }
            plugin.sendPrefixed(
                sender,
                "command.error.no-targets-sound",
                Map.of("displayName", result.displayName())
            );
            return true;
        }

        plugin.sendPrefixed(
            sender,
            "command.play.success",
            Map.of(
                "displayName", result.displayName(),
                "soundId", result.soundId(),
                "targetCount", Integer.toString(result.targetCount())
            )
        );
        return true;
    }

    private boolean handleLive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eltenasound.command.live")) {
            plugin.sendPrefixed(sender, "command.error.no-permission", Map.of());
            return true;
        }
        if (args.length < 2) {
            sendHelp(sender);
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "list" -> handleLiveList(sender);
            case "reload" -> handleLiveReload(sender);
            case "play" -> handleLivePlay(sender, args);
            case "send-manifest" -> handleLiveSendManifest(sender, args);
            case "send" -> handleLiveSend(sender, args);
            default -> {
                plugin.sendPrefixed(sender, "command.error.unknown-subcommand", Map.of());
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean handleLiveList(CommandSender sender) {
        plugin.sendPrefixed(
            sender,
            "command.live.list.header",
            Map.of("liveAssets", Integer.toString(plugin.liveAssetRegistry().count()))
        );
        for (LiveAssetDefinition definition : plugin.liveAssetPlaybackService().definitions()) {
            sender.sendMessage(
                plugin.language().text(
                    "command.live.list.entry",
                    Map.of(
                        "assetId", definition.id(),
                        "assetType", definition.type().payloadName(),
                        "version", Integer.toString(definition.version()),
                        "category", definition.category().name()
                    )
                )
            );
        }
        return true;
    }

    private boolean handleLiveReload(CommandSender sender) {
        plugin.reloadPluginState();
        plugin.sendPrefixed(
            sender,
            "command.live.reload.success",
            Map.of(
                "sounds", Integer.toString(plugin.soundRegistry().count()),
                "liveAssets", Integer.toString(plugin.liveAssetRegistry().count())
            )
        );
        plugin.logInfo("log.reload", plugin.runtimeStatePlaceholders());
        return true;
    }

    private boolean handleLivePlay(CommandSender sender, String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("usage");
        }
        if (!(sender instanceof Player player)) {
            plugin.sendPrefixed(sender, "command.live.error.player-only", Map.of());
            return true;
        }

        String assetId = args[2];
        LiveAssetDispatchResult result;
        try {
            result = plugin.liveAssetPlaybackService().play(player, assetId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("live:" + assetId, exception);
        }
        if (result.targetCount() == 0 && result.notReadyCount() > 0) {
            plugin.sendPrefixed(sender, "command.error.player-not-ready", Map.of());
            return true;
        }

        plugin.sendPrefixed(
            sender,
            "command.live.play.success",
            Map.of(
                "displayName", result.displayName(),
                "assetId", result.assetId()
            )
        );
        return true;
    }

    private boolean handleLiveSendManifest(CommandSender sender, String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("usage");
        }
        Player target = plugin.getServer().getPlayerExact(args[2]);
        if (target == null || !target.isOnline()) {
            plugin.sendPrefixed(sender, "command.live.error.unknown-player", Map.of("player", args[2]));
            return true;
        }
        if (!plugin.liveAssetPlaybackService().sendManifest(target)) {
            plugin.sendPrefixed(sender, "command.error.player-not-ready", Map.of());
            return true;
        }
        if (plugin.isDebugLogEnabled()) {
            plugin.logInfo("log.live-manifest-sent-by-command", Map.of("player", target.getName()));
        }
        plugin.sendPrefixed(
            sender,
            "command.live.send-manifest.success",
            Map.of("player", target.getName())
        );
        return true;
    }

    private boolean handleLiveSend(CommandSender sender, String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("usage");
        }
        Player target = plugin.getServer().getPlayerExact(args[2]);
        if (target == null || !target.isOnline()) {
            plugin.sendPrefixed(sender, "command.live.error.unknown-player", Map.of("player", args[2]));
            return true;
        }
        String assetId = args[3];
        try {
            if (!plugin.liveAssetPlaybackService().sendAsset(target, assetId)) {
                plugin.sendPrefixed(sender, "command.error.player-not-ready", Map.of());
                return true;
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("live:" + assetId, exception);
        }
        if (plugin.isDebugLogEnabled()) {
            plugin.logInfo(
                "log.live-transfer-started-by-command",
                Map.of(
                    "player", target.getName(),
                    "assetId", assetId
                )
            );
        }
        plugin.sendPrefixed(
            sender,
            "command.live.send.success",
            Map.of(
                "player", target.getName(),
                "assetId", assetId
            )
        );
        return true;
    }

    private boolean handleStop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eltenasound.command.stop")) {
            plugin.sendPrefixed(sender, "command.error.no-permission", Map.of());
            return true;
        }
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException("usage");
        }

        SoundCategory category;
        try {
            category = SoundCategory.fromConfig(args[1]);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("category:" + args[1], exception);
        }

        StopResult result = args.length == 3
            ? plugin.playbackService().stop(category, parseFadeOutMs(args[2]))
            : plugin.playbackService().stop(category, SoundPlaybackService.DEFAULT_STOP_FADE_OUT_MS);
        if (result.targetCount() == 0) {
            if (result.notReadyCount() > 0) {
                plugin.sendPrefixed(sender, "command.error.player-not-ready", Map.of());
                return true;
            }
            plugin.sendPrefixed(
                sender,
                "command.error.no-targets-category",
                Map.of("category", result.category().name())
            );
            return true;
        }

        plugin.sendPrefixed(
            sender,
            "command.stop.success",
            Map.of(
                "category", result.category().name(),
                "targetCount", Integer.toString(result.targetCount())
            )
        );
        return true;
    }

    private void sendHelp(CommandSender sender) {
        plugin.sendPrefixed(sender, "command.help.header", Map.of());
        sender.sendMessage(plugin.language().text("command.help.reload", Map.of()));
        sender.sendMessage(plugin.language().text("command.help.play", Map.of()));
        sender.sendMessage(plugin.language().text("command.help.stop", Map.of()));
        sender.sendMessage(plugin.language().text("command.help.live", Map.of()));
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase();
        return values.stream()
            .filter(value -> value.toLowerCase().startsWith(normalized))
            .sorted()
            .toList();
    }

    private long parseFadeOutMs(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0L) {
                throw new IllegalArgumentException("number:" + value);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("number:" + value, exception);
        }
    }
}
