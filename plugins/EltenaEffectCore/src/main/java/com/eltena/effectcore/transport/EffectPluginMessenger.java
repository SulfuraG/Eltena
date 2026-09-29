package com.eltena.effectcore.transport;

import com.eltena.effectcore.EltenaEffectCorePlugin;
import java.util.Map;
import org.bukkit.entity.Player;

public final class EffectPluginMessenger {
    public static final String EFFECT_CHANNEL_NAME = "eltena:effect";

    private final EltenaEffectCorePlugin plugin;

    public EffectPluginMessenger(EltenaEffectCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void registerChannel() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, EFFECT_CHANNEL_NAME);
    }

    public void unregisterChannel() {
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, EFFECT_CHANNEL_NAME);
    }

    public void send(Player player, String effectId, double power, byte[] bytes) {
        player.sendPluginMessage(plugin, EFFECT_CHANNEL_NAME, bytes);
        if (plugin.isDebugLogEnabled()) {
            plugin.logInfo(
                "log.dispatch",
                Map.of(
                    "effectId", effectId,
                    "player", player.getName(),
                    "bytes", Integer.toString(bytes.length),
                    "power", plugin.formatDecimal(power)
                )
            );
        }
    }
}
