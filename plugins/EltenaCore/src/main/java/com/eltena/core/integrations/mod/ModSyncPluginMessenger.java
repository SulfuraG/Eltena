package com.eltena.core.integrations.mod;

import com.eltena.core.application.logging.CoreLoggingSettings;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ModSyncPluginMessenger {
    public static final String SYNC_S2C_CHANNEL_NAME = "eltena:sync_s2c";
    public static final String REQUEST_C2S_CHANNEL_NAME = "eltena:request_c2s";
    private static final String SKILL_TREE_CHANNEL_ID = ModSyncChannel.SKILL_TREE.id();
    private static final String PLAYER_STATE_CHANNEL_ID = ModSyncChannel.PLAYER_STATE.id();
    private static final String ABILITY_STATE_CHANNEL_ID = ModSyncChannel.ABILITY_STATE.id();
    private static final String NOTIFICATION_CHANNEL_ID = ModSyncChannel.NOTIFICATION.id();
    private static final long MANA_SYNC_PERIOD_TICKS = 30L;

    private final JavaPlugin plugin;
    private final ModIntegrationService modIntegrationService;
    private final Map<UUID, Integer> lastKnownMana = new HashMap<>();
    private BukkitTask manaPollingTask;

    public ModSyncPluginMessenger(JavaPlugin plugin, ModIntegrationService modIntegrationService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.modIntegrationService = Objects.requireNonNull(modIntegrationService, "modIntegrationService");
    }

    public void registerChannels(PluginMessageListener incomingListener) {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, SYNC_S2C_CHANNEL_NAME);
        if (incomingListener != null) {
            plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, REQUEST_C2S_CHANNEL_NAME, incomingListener);
        }
        if (CoreLoggingSettings.syncDebugEnabled(plugin)) {
            plugin.getLogger().info(
                "[EltenaCore] Registered addon channels:"
                    + " outgoing=" + SYNC_S2C_CHANNEL_NAME
                    + " incoming=" + REQUEST_C2S_CHANNEL_NAME
                    + " incomingListener=" + (incomingListener != null)
            );
        }
        startManaPolling();
    }

    public void unregisterChannels() {
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, SYNC_S2C_CHANNEL_NAME);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, REQUEST_C2S_CHANNEL_NAME);
        if (CoreLoggingSettings.syncDebugEnabled(plugin)) {
            plugin.getLogger().info(
                "[EltenaCore] Unregistered addon channels:"
                    + " outgoing=" + SYNC_S2C_CHANNEL_NAME
                    + " incoming=" + REQUEST_C2S_CHANNEL_NAME
            );
        }
        stopManaPolling();
    }

    public boolean sendPlayerState(Player player) {
        try {
            modIntegrationService.syncVanillaLevelAndExp(player);
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().warning("[EltenaCore] Failed to sync vanilla level/exp for " + (player == null ? "unknown" : player.getName()) + ": " + exception.getMessage());
        }
        return sendChannel(player, ModSyncChannel.PLAYER_STATE, PLAYER_STATE_CHANNEL_ID);
    }

    public boolean sendSkillTree(Player player) {
        return sendChannel(player, ModSyncChannel.SKILL_TREE, SKILL_TREE_CHANNEL_ID);
    }

    public boolean sendAbilityState(Player player) {
        return sendChannel(player, ModSyncChannel.ABILITY_STATE, ABILITY_STATE_CHANNEL_ID);
    }

    public void sendInitialSyncBundle(Player player) {
        sendPlayerState(player);
        sendAbilityState(player);
        sendSkillTree(player);
    }

    public boolean sendNotification(Player player, String message, String level) {
        if (player == null || !player.isOnline() || !modIntegrationService.isEnabled() || message == null || message.isBlank()) {
            return false;
        }
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", stripColor(message));
        payload.put("level", level == null || level.isBlank() ? "info" : level);
        ModSyncEnvelope envelope = new ModSyncEnvelope(ModSyncChannel.NOTIFICATION, modIntegrationService.settings().protocolVersion(), java.time.Instant.now(), payload);
        return sendEnvelope(player, envelope, NOTIFICATION_CHANNEL_ID);
    }

    public int resendPlayerStateToOnlinePlayers() {
        return resendToOnlinePlayers(this::sendPlayerState);
    }

    public int resendSkillTreeToOnlinePlayers() {
        return resendToOnlinePlayers(this::sendSkillTree);
    }

    public int resendAbilityStateToOnlinePlayers() {
        return resendToOnlinePlayers(this::sendAbilityState);
    }

    public void resetPlayerState(UUID playerId) {
        if (playerId == null) {
            return;
        }
        lastKnownMana.remove(playerId);
    }

    private int resendToOnlinePlayers(java.util.function.Predicate<Player> sender) {
        int sent = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (sender.test(player)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean sendChannel(Player player, ModSyncChannel channel, String channelId) {
        if (player == null || !player.isOnline() || !modIntegrationService.isEnabled()) {
            return false;
        }
        try {
            ModIntegrationService.SyncResult result = modIntegrationService.syncPlayer(player.getUniqueId(), player.getName());
            ModSyncEnvelope envelope = result.envelopes().get(channel);
            if (envelope == null) {
                if (CoreLoggingSettings.syncDebugEnabled(plugin)) {
                    plugin.getLogger().warning(
                        "[EltenaCore] Missing addon payload:"
                            + " player=" + player.getName()
                            + " transport=" + SYNC_S2C_CHANNEL_NAME
                            + " payloadType=" + channel.id()
                    );
                }
                return false;
            }
            return sendEnvelope(player, envelope, channelId);
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().warning("[EltenaCore] Failed to send " + channelId + " sync to " + player.getName() + ": " + exception.getMessage());
            return false;
        }
    }

    private boolean sendEnvelope(Player player, ModSyncEnvelope envelope, String channelId) {
        String json = modIntegrationService.serialize(envelope);
        int byteLength = json.getBytes(StandardCharsets.UTF_8).length;
        if (CoreLoggingSettings.syncDebugEnabled(plugin)) {
            plugin.getLogger().info(
                "[EltenaCore] Sending addon payload:"
                    + " player=" + player.getName()
                    + " transport=" + SYNC_S2C_CHANNEL_NAME
                    + " payloadType=" + envelope.channel().id()
                    + " payloadChannel=" + channelId
                    + " bytes=" + byteLength
            );
        }
        try {
            player.sendPluginMessage(plugin, SYNC_S2C_CHANNEL_NAME, json.getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            plugin.getLogger().warning(
                "[EltenaCore] Failed addon payload:"
                    + " player=" + player.getName()
                    + " transport=" + SYNC_S2C_CHANNEL_NAME
                    + " payloadType=" + envelope.channel().id()
                    + " bytes=" + byteLength
                    + " reason=" + exception.getMessage()
            );
            return false;
        }
        if (PLAYER_STATE_CHANNEL_ID.equals(channelId) && envelope.payload() instanceof Map<?, ?> payloadMap) {
            Object mp = payloadValue(payloadMap, "mp", null);
            if (mp instanceof Number number) {
                lastKnownMana.put(player.getUniqueId(), number.intValue());
            }
        }
        if (CoreLoggingSettings.syncDebugEnabled(plugin)) {
            plugin.getLogger().info(successLogLine(player, channelId, envelope) + " bytes=" + byteLength);
        }
        return true;
    }

    private String successLogLine(Player player, String channelId, ModSyncEnvelope envelope) {
        if (PLAYER_STATE_CHANNEL_ID.equals(channelId) && envelope != null && envelope.payload() instanceof Map<?, ?> payloadMap) {
            Object level = payloadValue(payloadMap, "level", 1);
            Object exp = payloadValue(payloadMap, "exp", 0);
            Object maxExp = payloadValue(payloadMap, "max-exp", 100);
            Object hp = payloadValue(payloadMap, "hp", 20);
            Object maxHp = payloadValue(payloadMap, "max-hp", 20);
            Object mp = payloadValue(payloadMap, "mp", 20);
            Object maxMp = payloadValue(payloadMap, "max-mp", 20);
            Object defense = payloadValue(payloadMap, "defense", 0);
            Object job = payloadValue(payloadMap, "current-job-name", "");
            return "[EltenaCore] Sent player_state sync to " + player.getName()
                + " level=" + level
                + " exp=" + exp + "/" + maxExp
                + " hp=" + hp + "/" + maxHp
                + " mp=" + mp + "/" + maxMp
                + " defense=" + defense
                + " job=" + job
                + " channel=" + SYNC_S2C_CHANNEL_NAME;
        }
        return "[EltenaCore] Sent " + channelId + " sync to " + player.getName()
            + " nodes=" + extractNodeCount(envelope)
            + " channel=" + SYNC_S2C_CHANNEL_NAME;
    }

    private Object payloadValue(Map<?, ?> payloadMap, String key, Object fallback) {
        Object value = payloadMap.get(key);
        return value != null ? value : fallback;
    }

    private int extractNodeCount(ModSyncEnvelope envelope) {
        if (envelope == null || !(envelope.payload() instanceof Map<?, ?> payloadMap)) {
            return 0;
        }
        Object nodes = payloadMap.get("nodes");
        if (nodes instanceof java.util.List<?> nodeList) {
            return nodeList.size();
        }
        Object unlockedAbilities = payloadMap.get("unlocked-abilities");
        if (unlockedAbilities instanceof java.util.List<?> abilityList) {
            return abilityList.size();
        }
        Object titles = payloadMap.get("titles");
        return titles instanceof java.util.List<?> titleList ? titleList.size() : 0;
    }

    private String stripColor(String message) {
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', message));
    }

    private void startManaPolling() {
        stopManaPolling();
        manaPollingTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            this::pollManaChanges,
            MANA_SYNC_PERIOD_TICKS,
            MANA_SYNC_PERIOD_TICKS
        );
    }

    private void stopManaPolling() {
        if (manaPollingTask != null) {
            manaPollingTask.cancel();
            manaPollingTask = null;
        }
        lastKnownMana.clear();
    }

    private void pollManaChanges() {
        if (!modIntegrationService.isEnabled()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            try {
                ModIntegrationService.SyncResult result = modIntegrationService.syncPlayer(player.getUniqueId(), player.getName());
                ModSyncEnvelope envelope = result.envelopes().get(ModSyncChannel.PLAYER_STATE);
                if (envelope == null || !(envelope.payload() instanceof Map<?, ?> payloadMap)) {
                    continue;
                }
                Object mpValue = payloadValue(payloadMap, "mp", null);
                if (!(mpValue instanceof Number number)) {
                    continue;
                }
                int currentMana = number.intValue();
                Integer previousMana = lastKnownMana.get(player.getUniqueId());
                if (previousMana == null || previousMana != currentMana) {
                    sendPlayerState(player);
                }
            } catch (IOException | RuntimeException exception) {
                plugin.getLogger().warning("[EltenaCore] Failed to poll mana sync for " + player.getName() + ": " + exception.getMessage());
            }
        }
    }
}
