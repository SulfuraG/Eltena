package com.eltena.effectcore.command;

import com.eltena.effectcore.EltenaEffectCorePlugin;
import com.eltena.effectcore.playback.PlayResult;
import com.eltena.effectcore.playback.SequencePlayResult;
import com.eltena.effectcore.transport.VirtualCameraSyncService;
import com.eltena.effectcore.trigger.AreaTriggerAction;
import com.eltena.effectcore.trigger.AreaTriggerDefinition;
import com.eltena.effectcore.virtualcamera.VirtualCameraPreset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public final class EltenaEffectCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT_SUBCOMMANDS = List.of(
        "help",
        "reload",
        "camera",
        "play",
        "sequence",
        "mm",
        "mm-caster-play",
        "mm-caster-sequence",
        "trigger"
    );
    private static final List<String> CAMERA_ACTIONS = List.of("on", "off", "preset", "status");

    private final EltenaEffectCorePlugin plugin;

    public EltenaEffectCommand(EltenaEffectCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
        CommandSender sender,
        Command command,
        String label,
        String[] args
    ) {
        if (!sender.hasPermission("eltenaeffect.command.use")) {
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
                case "help" -> {
                    sendHelp(sender);
                    yield true;
                }
                case "reload" -> handleReload(sender);
                case "camera" -> handleCamera(sender, args);
                case "play" -> handlePlay(sender, args);
                case "sequence" -> handleSequence(sender, args);
                case "mm" -> handleMm(sender, args);
                case "mm-caster-play" -> handleMmCasterPlay(sender, args);
                case "mm-caster-sequence" -> handleMmCasterSequence(sender, args);
                case "trigger" -> handleTrigger(sender, args);
                default -> {
                    plugin.sendPrefixed(sender, "command.error.unknown-subcommand", Map.of());
                    sendHelp(sender);
                    yield true;
                }
            };
        } catch (IllegalArgumentException exception) {
            String message = exception.getMessage() == null ? "" : exception.getMessage();
            if (message.startsWith("number:")) {
                plugin.sendPrefixed(sender, "command.error.invalid-number", Map.of());
            } else if (message.startsWith("world:")) {
                plugin.sendPrefixed(
                    sender,
                    "command.error.world-not-found",
                    Map.of("world", message.substring("world:".length()))
                );
            } else if (message.startsWith("effect:")) {
                plugin.sendPrefixed(
                    sender,
                    "command.error.unknown-effect",
                    Map.of("effectId", message.substring("effect:".length()))
                );
            } else if (message.startsWith("sequence:")) {
                plugin.sendPrefixed(
                    sender,
                    "command.error.unknown-sequence",
                    Map.of("sequenceId", message.substring("sequence:".length()))
                );
            } else if (message.startsWith("uuid:")) {
                plugin.sendPrefixed(sender, "command.error.invalid-uuid", Map.of());
            } else if (message.startsWith("entity:")) {
                plugin.sendPrefixed(
                    sender,
                    "command.error.entity-not-found",
                    Map.of("entityUuid", message.substring("entity:".length()))
                );
            } else if (message.startsWith("entity-dead:")) {
                plugin.sendPrefixed(
                    sender,
                    "command.error.entity-dead",
                    Map.of("entityUuid", message.substring("entity-dead:".length()))
                );
            } else if (message.startsWith("player:")) {
                plugin.sendPrefixed(
                    sender,
                    "command.error.player-not-found",
                    Map.of("player", message.substring("player:".length()))
                );
            } else if (message.startsWith("preset:")) {
                plugin.sendPrefixedLiteral(
                    sender,
                    "&cVirtualCamera preset '" + message.substring("preset:".length()) + "' は見つからないか無効です。"
                );
            } else if (message.startsWith("usage")) {
                sendHelp(sender);
            } else {
                plugin.sendPrefixed(sender, "command.error.player-or-location-required", Map.of());
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
        return computeTabCompletion(args);
    }

    private List<String> computeTabCompletion(String[] args) {
        if (args.length == 1) {
            return filter(ROOT_SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && "camera".equalsIgnoreCase(args[0])) {
            if ("virtual".equalsIgnoreCase(args[1])) {
                return CAMERA_ACTIONS;
            }
            return filter(List.of("virtual"), args[1]);
        }
        if (args.length == 3 && isCameraVirtual(args)) {
            if (isCameraAction(args[2])) {
                return cameraTargets();
            }
            return filter(CAMERA_ACTIONS, args[2]);
        }
        if (args.length == 4 && isCameraVirtual(args)) {
            if (isCameraAction(args[2])) {
                return completeCameraActionFirst(args[2], args[3]);
            }
            return Collections.emptyList();
        }
        if (args.length == 5 && isCameraVirtual(args)) {
            if (isCameraAction(args[2]) && requiresPreset(args[2])) {
                return filter(cameraPresetIds(), args[4]);
            }
        }
        if (args.length == 2 && "play".equalsIgnoreCase(args[0])) {
            return filter(new ArrayList<>(plugin.effectRegistry().effectIds()), args[1]);
        }
        if (args.length == 2 && "sequence".equalsIgnoreCase(args[0])) {
            return filter(new ArrayList<>(plugin.effectSequenceRegistry().sequenceIds()), args[1]);
        }
        if (args.length == 2 && "mm-caster-play".equalsIgnoreCase(args[0])) {
            return filter(new ArrayList<>(plugin.effectRegistry().effectIds()), args[1]);
        }
        if (args.length == 2 && "mm-caster-sequence".equalsIgnoreCase(args[0])) {
            return filter(new ArrayList<>(plugin.effectSequenceRegistry().sequenceIds()), args[1]);
        }
        if (args.length == 2 && "mm".equalsIgnoreCase(args[0])) {
            return filter(List.of("play", "sequence"), args[1]);
        }
        if (args.length == 2 && "trigger".equalsIgnoreCase(args[0])) {
            return filter(List.of("list", "reload", "debug"), args[1]);
        }
        if (args.length == 3 && "mm".equalsIgnoreCase(args[0]) && "play".equalsIgnoreCase(args[1])) {
            return filter(new ArrayList<>(plugin.effectRegistry().effectIds()), args[2]);
        }
        if (args.length == 3 && "mm".equalsIgnoreCase(args[0]) && "sequence".equalsIgnoreCase(args[1])) {
            return filter(new ArrayList<>(plugin.effectSequenceRegistry().sequenceIds()), args[2]);
        }
        if (args.length == 3 && "trigger".equalsIgnoreCase(args[0]) && "debug".equalsIgnoreCase(args[1])) {
            return filter(
                plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList(),
                args[2]
            );
        }
        if (args.length == 3 && isPlaybackSubcommand(args[0])) {
            return filter(List.of("here", "at"), args[2]);
        }
        if (args.length == 4 && isPlaybackSubcommand(args[0]) && "at".equalsIgnoreCase(args[2])) {
            return filter(
                plugin.getServer().getWorlds().stream().map(World::getName).toList(),
                args[3]
            );
        }
        if (args.length == 4 && "mm".equalsIgnoreCase(args[0]) && isMmPlaybackSubcommand(args[1])) {
            return filter(
                plugin.getServer().getWorlds().stream().map(World::getName).toList(),
                args[3]
            );
        }
        return Collections.emptyList();
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("eltenaeffect.command.reload")) {
            plugin.sendPrefixed(sender, "command.error.no-permission", Map.of());
            return true;
        }
        performReload(sender);
        return true;
    }

    private boolean handleCamera(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eltenaeffect.command.camera")) {
            plugin.sendPrefixed(sender, "command.error.no-permission", Map.of());
            return true;
        }
        if (args.length < 3 || !"virtual".equalsIgnoreCase(args[1])) {
            sendHelp(sender);
            return true;
        }

        if (args.length >= 4 && isCameraAction(args[2])) {
            return handleCameraActionFirst(sender, args[2].toLowerCase(), args);
        }

        sendHelp(sender);
        return true;
    }

    private boolean handleCameraActionFirst(CommandSender sender, String action, String[] args) {
        if (args.length < 4) {
            throw new IllegalArgumentException("usage");
        }
        if ("preset".equalsIgnoreCase(action) && args.length != 5) {
            throw new IllegalArgumentException("usage");
        }
        if ("on".equalsIgnoreCase(action) && args.length != 4 && args.length != 5) {
            throw new IllegalArgumentException("usage");
        }
        if (!acceptsPreset(action) && args.length != 4) {
            throw new IllegalArgumentException("usage");
        }

        String targetSelector = args[3];
        if ("all".equalsIgnoreCase(targetSelector)) {
            return handleCameraAll(sender, action, args.length >= 5 ? args[4] : null);
        }

        Player player = plugin.getServer().getPlayerExact(targetSelector);
        if (player == null || !player.isOnline()) {
            throw new IllegalArgumentException("player:" + targetSelector);
        }
        return handleCameraPlayer(sender, player, action, args.length >= 5 ? args[4] : null);
    }

    private boolean handleTrigger(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eltenaeffect.command.trigger")) {
            plugin.sendPrefixed(sender, "command.error.no-permission", Map.of());
            return true;
        }
        if (args.length < 2) {
            sendHelp(sender);
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "list" -> handleTriggerList(sender);
            case "reload" -> handleTriggerReload(sender);
            case "debug" -> handleTriggerDebug(sender, args);
            default -> {
                plugin.sendPrefixed(sender, "command.error.unknown-subcommand", Map.of());
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean handleTriggerList(CommandSender sender) {
        List<AreaTriggerDefinition> triggers = plugin.areaTriggerRegistry().definitions();
        plugin.sendPrefixed(
            sender,
            "command.trigger.list-header",
            Map.of("triggerCount", Integer.toString(triggers.size()))
        );
        if (triggers.isEmpty()) {
            sender.sendMessage(plugin.language().text("command.trigger.list-empty", Map.of()));
            return true;
        }

        for (AreaTriggerDefinition trigger : triggers) {
            String actions = trigger.actions()
                .stream()
                .map(this::formatActionSummary)
                .reduce((left, right) -> left + ", " + right)
                .orElse("-");
            sender.sendMessage(
                plugin.language().text(
                    "command.trigger.list-entry",
                    Map.of(
                        "triggerId", trigger.id(),
                        "displayName", trigger.displayName(),
                        "enabled", Boolean.toString(trigger.enabled()),
                        "world", trigger.worldName(),
                        "cooldownMs", Long.toString(trigger.cooldownMs()),
                        "actions", actions
                    )
                )
            );
        }
        return true;
    }

    private boolean handleTriggerReload(CommandSender sender) {
        performReload(sender);
        return true;
    }

    private boolean handleCameraAll(CommandSender sender, String action, String presetArgument) {
        if (plugin.getServer().getOnlinePlayers().isEmpty()) {
            plugin.sendPrefixedLiteral(sender, "&eオンラインプレイヤーがいないため VirtualCamera を同期できません。");
            return true;
        }

        return switch (action) {
            case "on" -> {
                if (!plugin.virtualCameraConfig().enabled()) {
                    plugin.sendPrefixedLiteral(sender, "&cVirtualCamera は camera.yml で無効です。");
                    yield true;
                }
                String preset = resolveOptionalOnPreset(presetArgument);
                int affected = plugin.virtualCameraSyncService().enableAll(preset);
                plugin.sendPrefixedLiteral(sender, "&a全員の VirtualCamera を有効化しました。preset=" + preset + " 対象=" + affected);
                yield true;
            }
            case "off" -> {
                int affected = plugin.virtualCameraSyncService().disableAll();
                plugin.sendPrefixedLiteral(sender, "&a全員の VirtualCamera を無効化しました。対象=" + affected);
                yield true;
            }
            case "preset" -> {
                String preset = requirePresetArgument(presetArgument);
                int affected = plugin.virtualCameraSyncService().setPresetForAll(preset);
                plugin.sendPrefixedLiteral(sender, "&a全員の VirtualCamera preset を切り替えました。preset=" + preset + " 対象=" + affected);
                yield true;
            }
            case "status" -> {
                plugin.sendPrefixedLiteral(sender, "&fall VirtualCamera 状態: オンラインプレイヤーごとに個別状態で管理されています。");
                for (Player online : plugin.getServer().getOnlinePlayers()) {
                    sendCameraStatus(sender, online);
                }
                yield true;
            }
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean handleCameraPlayer(CommandSender sender, Player player, String action, String presetArgument) {
        return switch (action) {
            case "on" -> {
                if (!plugin.virtualCameraConfig().enabled()) {
                    plugin.sendPrefixedLiteral(sender, "&cVirtualCamera は camera.yml で無効です。");
                    yield true;
                }
                String preset = resolveOptionalOnPreset(presetArgument);
                if (!plugin.virtualCameraSyncService().enablePlayer(player, preset)) {
                    plugin.sendPrefixedLiteral(sender, "&cVirtualCamera を有効化できませんでした。preset を確認してください。");
                    yield true;
                }
                plugin.sendPrefixedLiteral(sender, "&a" + player.getName() + " の VirtualCamera を有効化しました。preset=" + preset);
                yield true;
            }
            case "off" -> {
                plugin.virtualCameraSyncService().disablePlayer(player);
                plugin.sendPrefixedLiteral(sender, "&a" + player.getName() + " の VirtualCamera を無効化しました。");
                yield true;
            }
            case "preset" -> {
                String preset = requirePresetArgument(presetArgument);
                if (!plugin.virtualCameraSyncService().setPlayerPreset(player, preset)) {
                    plugin.sendPrefixedLiteral(sender, "&cVirtualCamera preset を切り替えできませんでした。preset を確認してください。");
                    yield true;
                }
                plugin.sendPrefixedLiteral(sender, "&a" + player.getName() + " の VirtualCamera preset を " + preset + " に切り替えました。");
                yield true;
            }
            case "status" -> {
                sendCameraStatus(sender, player);
                yield true;
            }
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean handleTriggerDebug(CommandSender sender, String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("usage");
        }

        Player player = plugin.getServer().getPlayerExact(args[2]);
        if (player == null || !player.isOnline()) {
            throw new IllegalArgumentException("player:" + args[2]);
        }

        List<AreaTriggerDefinition> triggers = plugin.areaTriggerService().activeDefinitionsAt(player);
        plugin.sendPrefixed(
            sender,
            "command.trigger.debug-header",
            Map.of(
                "player", player.getName(),
                "world", player.getWorld().getName(),
                "x", plugin.formatDecimal(player.getLocation().getX()),
                "y", plugin.formatDecimal(player.getLocation().getY()),
                "z", plugin.formatDecimal(player.getLocation().getZ())
            )
        );
        if (triggers.isEmpty()) {
            sender.sendMessage(plugin.language().text("command.trigger.debug-empty", Map.of()));
            return true;
        }

        for (AreaTriggerDefinition trigger : triggers) {
            sender.sendMessage(
                plugin.language().text(
                    "command.trigger.debug-entry",
                    Map.of(
                        "triggerId", trigger.id(),
                        "displayName", trigger.displayName(),
                        "enabled", Boolean.toString(trigger.enabled())
                    )
                )
            );
        }
        return true;
    }

    private boolean handlePlay(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eltenaeffect.command.play")) {
            plugin.sendPrefixed(sender, "command.error.no-permission", Map.of());
            return true;
        }
        if (args.length < 2) {
            sendHelp(sender);
            return true;
        }

        String effectId = args[1];
        Location sourceLocation = resolveSourceLocation(sender, args, 2);
        return playEffectFromLocation(sender, effectId, sourceLocation);
    }

    private boolean handleSequence(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eltenaeffect.command.sequence")) {
            plugin.sendPrefixed(sender, "command.error.no-permission", Map.of());
            return true;
        }
        if (args.length < 2) {
            sendHelp(sender);
            return true;
        }

        String sequenceId = args[1];
        Location sourceLocation = resolveSourceLocation(sender, args, 2);
        return playSequenceFromLocation(sender, sequenceId, sourceLocation);
    }

    private boolean handleMm(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eltenaeffect.command.mm")) {
            plugin.sendPrefixed(sender, "command.error.no-permission", Map.of());
            return true;
        }
        if (args.length < 7) {
            sendHelp(sender);
            return true;
        }

        String mode = args[1].toLowerCase();
        return switch (mode) {
            case "play" -> handleMmPlay(sender, args);
            case "sequence" -> handleMmSequence(sender, args);
            default -> {
                plugin.sendPrefixed(sender, "command.error.unknown-subcommand", Map.of());
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean handleMmPlay(CommandSender sender, String[] args) {
        if (args.length != 7) {
            throw new IllegalArgumentException("usage");
        }

        String effectId = args[2];
        Location sourceLocation = parseExplicitLocation(args[3], args[4], args[5], args[6]);
        return playEffectFromLocation(sender, effectId, sourceLocation);
    }

    private boolean handleMmSequence(CommandSender sender, String[] args) {
        if (args.length != 7) {
            throw new IllegalArgumentException("usage");
        }

        String sequenceId = args[2];
        Location sourceLocation = parseExplicitLocation(args[3], args[4], args[5], args[6]);
        return playSequenceFromLocation(sender, sequenceId, sourceLocation);
    }

    private boolean handleMmCasterPlay(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eltenaeffect.command.mmcaster")) {
            plugin.sendPrefixed(sender, "command.error.no-permission", Map.of());
            return true;
        }
        if (args.length != 3) {
            throw new IllegalArgumentException("usage");
        }

        String effectId = args[1];
        Location sourceLocation = resolveEntityLocation(args[2]);
        return playEffectFromLocation(sender, effectId, sourceLocation);
    }

    private boolean handleMmCasterSequence(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eltenaeffect.command.mmcaster")) {
            plugin.sendPrefixed(sender, "command.error.no-permission", Map.of());
            return true;
        }
        if (args.length != 3) {
            throw new IllegalArgumentException("usage");
        }

        String sequenceId = args[1];
        Location sourceLocation = resolveEntityLocation(args[2]);
        return playSequenceFromLocation(sender, sequenceId, sourceLocation);
    }

    private void sendHelp(CommandSender sender) {
        plugin.sendPrefixed(sender, "command.help.header", Map.of());
        plugin.sendPrefixedLiteral(sender, "&e/eltenaeffect help &7- コマンド一覧を表示します。");
        plugin.sendPrefixedLiteral(sender, "&e/eltenaeffect camera virtual on <player> [preset] &7- 指定プレイヤーの VirtualCamera を有効化します。preset 省略時は default-preset を使います。");
        plugin.sendPrefixedLiteral(sender, "&e/eltenaeffect camera virtual off <player> &7- 指定プレイヤーの VirtualCamera を無効化します。");
        plugin.sendPrefixedLiteral(sender, "&e/eltenaeffect camera virtual preset <player> <preset> &7- 指定プレイヤーの VirtualCamera preset を切り替えます。");
        plugin.sendPrefixedLiteral(sender, "&e/eltenaeffect camera virtual status <player> &7- 指定プレイヤーの VirtualCamera 状態を表示します。");
        plugin.sendPrefixedLiteral(sender, "&e/eltenaeffect camera virtual on all [preset] &7- 全員の VirtualCamera を有効化します。preset 省略時は default-preset を使います。");
        plugin.sendPrefixedLiteral(sender, "&e/eltenaeffect camera virtual off all &7- 全員の VirtualCamera を無効化します。");
        plugin.sendPrefixedLiteral(sender, "&e/eltenaeffect camera virtual preset all <preset> &7- 全員の VirtualCamera preset を切り替えます。");
        plugin.sendPrefixedLiteral(sender, "&e/eltenaeffect camera virtual status all &7- 全員の VirtualCamera 状態を表示します。");
        sender.sendMessage(plugin.language().text("command.help.reload", Map.of()));
        sender.sendMessage(plugin.language().text("command.help.play-self", Map.of()));
        sender.sendMessage(plugin.language().text("command.help.play-here", Map.of()));
        sender.sendMessage(plugin.language().text("command.help.play-at", Map.of()));
        sender.sendMessage(plugin.language().text("command.help.mm-play", Map.of()));
        sender.sendMessage(plugin.language().text("command.help.trigger-list", Map.of()));
        sender.sendMessage(plugin.language().text("command.help.trigger-reload", Map.of()));
        sender.sendMessage(plugin.language().text("command.help.trigger-debug", Map.of()));
        sender.sendMessage(plugin.language().text("command.help.sequence-self", Map.of()));
        sender.sendMessage(plugin.language().text("command.help.sequence-here", Map.of()));
        sender.sendMessage(plugin.language().text("command.help.sequence-at", Map.of()));
        sender.sendMessage(plugin.language().text("command.help.mm-sequence", Map.of()));
        sender.sendMessage(plugin.language().text("command.help.mm-caster-play", Map.of()));
        sender.sendMessage(plugin.language().text("command.help.mm-caster-sequence", Map.of()));
    }

    private void sendCameraStatus(CommandSender sender, Player player) {
        VirtualCameraSyncService.CameraStatus status = plugin.virtualCameraSyncService().status(player);
        String presetId = status.activePresetId() == null ? "<none>" : status.activePresetId();
        String displayName = status.preset() == null ? "<none>" : status.preset().displayName();
        plugin.sendPrefixedLiteral(
            sender,
            "&f" + player.getName()
                + " VirtualCamera 状態: desiredEnabled=" + status.desiredEnabled()
                + " effectiveEnabled=" + status.effectiveEnabled()
                + " activePreset=" + presetId
                + " displayName=" + displayName
        );
    }

    private Location resolveSourceLocation(
        CommandSender sender,
        String[] args,
        int modeIndex
    ) {
        if (args.length == modeIndex) {
            return requireSenderLocation(sender);
        }
        if ("here".equalsIgnoreCase(args[modeIndex])) {
            return requireSenderLocation(sender);
        }
        if ("at".equalsIgnoreCase(args[modeIndex]) && args.length >= modeIndex + 5) {
            return parseExplicitLocation(
                args[modeIndex + 1],
                args[modeIndex + 2],
                args[modeIndex + 3],
                args[modeIndex + 4]
            );
        }
        throw new IllegalArgumentException("usage");
    }

    private Location requireSenderLocation(CommandSender sender) {
        if (sender instanceof Entity entity) {
            return entity.getLocation();
        }
        if (sender instanceof BlockCommandSender blockSender) {
            return blockSender.getBlock().getLocation().add(0.5D, 0.0D, 0.5D);
        }
        if (sender instanceof ProxiedCommandSender proxiedSender) {
            try {
                return requireSenderLocation(proxiedSender.getCallee());
            } catch (IllegalArgumentException ignored) {
                return requireSenderLocation(proxiedSender.getCaller());
            }
        }
        throw new IllegalArgumentException("sender-location");
    }

    private Location parseExplicitLocation(
        String worldName,
        String x,
        String y,
        String z
    ) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            throw new IllegalArgumentException("world:" + worldName);
        }

        try {
            return new Location(
                world,
                Double.parseDouble(x),
                Double.parseDouble(y),
                Double.parseDouble(z)
            );
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("number:" + exception.getMessage(), exception);
        }
    }

    private Location resolveEntityLocation(String entityUuidText) {
        final UUID entityUuid;
        try {
            entityUuid = UUID.fromString(entityUuidText);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("uuid:" + entityUuidText, exception);
        }

        Entity entity = plugin.getServer().getEntity(entityUuid);
        if (entity == null) {
            throw new IllegalArgumentException("entity:" + entityUuid);
        }
        if (entity.isDead() || !entity.isValid()) {
            throw new IllegalArgumentException("entity-dead:" + entityUuid);
        }
        return entity.getLocation();
    }

    private boolean playEffectFromLocation(CommandSender sender, String effectId, Location sourceLocation) {
        PlayResult result;
        try {
            result = plugin.playbackService().play(effectId, sourceLocation);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("effect:" + effectId, exception);
        }

        if (result.targetCount() == 0) {
            plugin.sendPrefixed(
                sender,
                "command.error.no-targets",
                Map.of("displayName", result.displayName())
            );
            return true;
        }

        plugin.sendPrefixed(
            sender,
            "command.play.success",
            Map.of(
                "displayName", result.displayName(),
                "effectId", result.effectId(),
                "targetCount", Integer.toString(result.targetCount())
            )
        );
        if (result.scheduledRepeatCount() > 0) {
            plugin.sendPrefixed(
                sender,
                "command.play.success-repeat",
                Map.of("repeatCount", Integer.toString(result.scheduledRepeatCount()))
            );
        }
        return true;
    }

    private boolean playSequenceFromLocation(CommandSender sender, String sequenceId, Location sourceLocation) {
        SequencePlayResult result;
        try {
            result = plugin.sequencePlaybackService().play(sequenceId, sourceLocation);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("sequence:" + sequenceId, exception);
        }

        plugin.sendPrefixed(
            sender,
            "command.sequence.success",
            Map.of(
                "displayName", result.displayName(),
                "sequenceId", result.sequenceId(),
                "stepCount", Integer.toString(result.stepCount())
            )
        );
        return true;
    }

    private List<String> cameraTargets() {
        List<String> targets = new ArrayList<>();
        targets.add("all");
        targets.addAll(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList());
        return targets;
    }

    private List<String> completeCameraActionFirst(String action, String currentToken) {
        List<String> targets = cameraTargets();
        if (requiresPreset(action) && isCameraTarget(currentToken)) {
            return cameraPresetIds();
        }
        return filter(targets, currentToken);
    }

    private List<String> cameraPresetIds() {
        return new ArrayList<>(plugin.virtualCameraConfig().presets().keySet());
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase();
        return values.stream()
            .filter(value -> value.toLowerCase().startsWith(normalized))
            .toList();
    }

    private boolean isPlaybackSubcommand(String value) {
        return "play".equalsIgnoreCase(value) || "sequence".equalsIgnoreCase(value);
    }

    private boolean isMmPlaybackSubcommand(String value) {
        return "play".equalsIgnoreCase(value) || "sequence".equalsIgnoreCase(value);
    }

    private boolean isCameraVirtual(String[] args) {
        return args.length >= 2
            && "camera".equalsIgnoreCase(args[0])
            && "virtual".equalsIgnoreCase(args[1]);
    }

    private boolean isCameraAction(String value) {
        return value != null && CAMERA_ACTIONS.stream().anyMatch(action -> action.equalsIgnoreCase(value));
    }

    private boolean requiresPreset(String action) {
        return "on".equalsIgnoreCase(action) || "preset".equalsIgnoreCase(action);
    }

    private boolean acceptsPreset(String action) {
        return "on".equalsIgnoreCase(action) || "preset".equalsIgnoreCase(action);
    }

    private boolean isCameraTarget(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return cameraTargets().stream().anyMatch(target -> target.equalsIgnoreCase(value));
    }

    private String requirePresetArgument(String presetArgument) {
        if (presetArgument == null || presetArgument.isBlank()) {
            throw new IllegalArgumentException("usage");
        }
        return requirePreset(presetArgument);
    }

    private String resolveOptionalOnPreset(String presetArgument) {
        if (presetArgument == null || presetArgument.isBlank()) {
            String defaultPreset = plugin.virtualCameraConfig().resolvedDefaultPreset();
            if (defaultPreset == null || defaultPreset.isBlank()) {
                throw new IllegalArgumentException("preset:<default>");
            }
            return defaultPreset;
        }
        return requirePreset(presetArgument);
    }

    private void performReload(CommandSender sender) {
        plugin.reloadPluginState();
        plugin.sendPrefixed(sender, "command.reload.success", plugin.runtimeStatePlaceholders());
        plugin.logInfo("log.reload", plugin.runtimeStatePlaceholders());
        plugin.getLogger().info(
            "[EltenaEffectCore] EltenaEffectCore を再読み込みしました。"
                + " effects=" + plugin.effectRegistry().count()
                + " sequences=" + plugin.effectSequenceRegistry().count()
                + " triggers=" + plugin.areaTriggerRegistry().count()
                + " realtimeEvents=" + plugin.realtimeEventRegistry().count()
                + " locale=" + plugin.language().locale()
        );
    }

    private String formatActionSummary(AreaTriggerAction action) {
        return action.type().name().toLowerCase() + ":" + action.id();
    }

    private String requirePreset(String presetId) {
        if (presetId == null || presetId.isBlank()) {
            throw new IllegalArgumentException("preset:<blank>");
        }
        VirtualCameraPreset preset = plugin.virtualCameraConfig().preset(presetId);
        if (preset == null || !preset.enabled()) {
            throw new IllegalArgumentException("preset:" + presetId);
        }
        return preset.id();
    }
}
