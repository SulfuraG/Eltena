package com.eltena.effectcore.trigger;

import com.eltena.effectcore.EltenaEffectCorePlugin;
import com.eltena.effectcore.config.AreaTriggerRegistry;
import com.eltena.effectcore.playback.EffectPlaybackService;
import com.eltena.effectcore.playback.EffectSequencePlaybackService;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

public final class AreaTriggerService implements Listener {
    private static final long POLL_INTERVAL_TICKS = 20L;

    private final EltenaEffectCorePlugin plugin;
    private final AreaTriggerRegistry triggerRegistry;
    private final EffectPlaybackService playbackService;
    private final EffectSequencePlaybackService sequencePlaybackService;
    private final Map<UUID, Set<String>> activeTriggerIdsByPlayer = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldownsByPlayer = new HashMap<>();
    private BukkitTask pollTask;

    public AreaTriggerService(
        EltenaEffectCorePlugin plugin,
        AreaTriggerRegistry triggerRegistry,
        EffectPlaybackService playbackService,
        EffectSequencePlaybackService sequencePlaybackService
    ) {
        this.plugin = plugin;
        this.triggerRegistry = triggerRegistry;
        this.playbackService = playbackService;
        this.sequencePlaybackService = sequencePlaybackService;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        rebuildPlayerStates();
        this.pollTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            this::pollOnlinePlayers,
            POLL_INTERVAL_TICKS,
            POLL_INTERVAL_TICKS
        );
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
        activeTriggerIdsByPlayer.clear();
        cooldownsByPlayer.clear();
    }

    public List<AreaTriggerDefinition> activeDefinitionsAt(Player player) {
        if (player == null) {
            return List.of();
        }
        return triggerRegistry.matchingDefinitions(player.getLocation());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        initializePlayerState(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        activeTriggerIdsByPlayer.remove(playerId);
        cooldownsByPlayer.remove(playerId);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null) {
            return;
        }
        if (from.getWorld() == to.getWorld()
            && from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        checkPlayer(event.getPlayer(), to);
    }

    private void pollOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            checkPlayer(player, player.getLocation());
        }
    }

    private void rebuildPlayerStates() {
        activeTriggerIdsByPlayer.clear();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            initializePlayerState(player);
        }
    }

    private void initializePlayerState(Player player) {
        activeTriggerIdsByPlayer.put(
            player.getUniqueId(),
            currentTriggerIds(player.getLocation(), false)
        );
    }

    private void checkPlayer(Player player, Location location) {
        if (player == null || !player.isOnline() || location == null || location.getWorld() == null) {
            return;
        }

        Set<String> previousInside = activeTriggerIdsByPlayer.get(player.getUniqueId());
        Set<String> currentInside = currentTriggerIds(location, false);
        if (previousInside == null) {
            activeTriggerIdsByPlayer.put(player.getUniqueId(), currentInside);
            return;
        }

        for (String triggerId : currentInside) {
            if (!previousInside.contains(triggerId)) {
                AreaTriggerDefinition trigger = triggerRegistry.find(triggerId);
                if (trigger != null && trigger.enabled()) {
                    handleEnter(player, location, trigger);
                }
            }
        }

        previousInside.clear();
        previousInside.addAll(currentInside);
    }

    private Set<String> currentTriggerIds(Location location, boolean enabledOnly) {
        LinkedHashSet<String> triggerIds = new LinkedHashSet<>();
        for (AreaTriggerDefinition trigger : triggerRegistry.definitions()) {
            if (enabledOnly && !trigger.enabled()) {
                continue;
            }
            if (trigger.matches(location)) {
                triggerIds.add(trigger.id());
            }
        }
        return triggerIds;
    }

    private void handleEnter(Player player, Location location, AreaTriggerDefinition trigger) {
        if (plugin.isDebugLogEnabled()) {
            plugin.logInfo(
                "log.area-trigger-player-entered",
                Map.of(
                    "player", player.getName(),
                    "triggerId", trigger.id()
                )
            );
        }

        String bypassPermission = trigger.permissionBypass();
        if (bypassPermission != null && player.hasPermission(bypassPermission)) {
            if (plugin.isDebugLogEnabled()) {
                plugin.logInfo(
                    "log.area-trigger-bypassed",
                    Map.of(
                        "player", player.getName(),
                        "triggerId", trigger.id(),
                        "permission", bypassPermission
                    )
                );
            }
            return;
        }

        long now = System.currentTimeMillis();
        long remainingCooldown = remainingCooldownMs(player.getUniqueId(), trigger.id(), trigger.cooldownMs(), now);
        if (remainingCooldown > 0L) {
            if (plugin.isDebugLogEnabled()) {
                plugin.logInfo(
                    "log.area-trigger-cooldown-active",
                    Map.of(
                        "player", player.getName(),
                        "triggerId", trigger.id(),
                        "remainingMs", Long.toString(remainingCooldown)
                    )
                );
            }
            return;
        }

        recordActivation(player.getUniqueId(), trigger.id(), now);
        for (AreaTriggerAction action : trigger.actions()) {
            if (plugin.isDebugLogEnabled()) {
                plugin.logInfo(
                    "log.area-trigger-action-play",
                    Map.of(
                        "player", player.getName(),
                        "triggerId", trigger.id(),
                        "actionType", action.type().name(),
                        "actionId", action.id()
                    )
                );
            }
            try {
                switch (action.type()) {
                    case EFFECT -> playbackService.play(action.id(), location);
                    case SEQUENCE -> sequencePlaybackService.play(action.id(), location, List.of(player), player);
                }
            } catch (IllegalArgumentException exception) {
                plugin.logWarning(
                    "log.area-trigger-action-missing",
                    Map.of(
                        "triggerId", trigger.id(),
                        "actionType", action.type().name(),
                        "actionId", action.id()
                    )
                );
            }
        }
    }

    private long remainingCooldownMs(
        UUID playerId,
        String triggerId,
        long cooldownMs,
        long now
    ) {
        if (cooldownMs <= 0L) {
            return 0L;
        }
        Map<String, Long> triggerCooldowns = cooldownsByPlayer.get(playerId);
        if (triggerCooldowns == null) {
            return 0L;
        }
        Long lastTriggeredAt = triggerCooldowns.get(triggerId);
        if (lastTriggeredAt == null) {
            return 0L;
        }
        long elapsed = now - lastTriggeredAt;
        return elapsed >= cooldownMs ? 0L : cooldownMs - elapsed;
    }

    private void recordActivation(UUID playerId, String triggerId, long now) {
        cooldownsByPlayer
            .computeIfAbsent(playerId, ignored -> new HashMap<>())
            .put(triggerId, now);
    }
}
