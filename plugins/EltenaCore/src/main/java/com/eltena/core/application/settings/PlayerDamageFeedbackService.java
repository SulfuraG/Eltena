package com.eltena.core.application.settings;

import com.eltena.core.bootstrap.ServiceRegistry;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerDamageFeedbackService {

    private static final DecimalFormat DPS_FORMAT = new DecimalFormat(
        "0.0",
        DecimalFormatSymbols.getInstance(Locale.ROOT)
    );

    private final ServiceRegistry services;
    private final Map<UUID, DpsSession> sessions = new ConcurrentHashMap<>();

    public PlayerDamageFeedbackService(ServiceRegistry services) {
        this.services = services;
    }

    public void recordHit(Player player, long damage) {
        if (player == null || !player.isOnline() || damage <= 0L) {
            return;
        }
        if (!services.playerOptionSettingsService().isDpsChatEnabled(player.getUniqueId())) {
            return;
        }

        Instant now = Instant.now();
        long windowSeconds = Math.max(1L, services.plugin().getConfig().getLong("player-options.dps.window-seconds", 5L));
        DpsSession session = sessions.compute(player.getUniqueId(), (uuid, current) -> {
            if (current == null || Duration.between(current.lastHitAt(), now).getSeconds() > windowSeconds) {
                return new DpsSession(now, now, damage);
            }
            return new DpsSession(current.startedAt(), now, current.totalDamage() + damage);
        });

        double elapsedSeconds = Math.max(1.0D, Math.max(0.001D, Duration.between(session.startedAt(), now).toMillis() / 1000.0D));
        double dps = session.totalDamage() / elapsedSeconds;
        player.sendMessage(color(services.messages().get(
            "settings.dps.report",
            "damage", damage,
            "total", session.totalDamage(),
            "seconds", DPS_FORMAT.format(elapsedSeconds),
            "dps", DPS_FORMAT.format(dps)
        )));
    }

    public void resetPlayerState(UUID playerId) {
        if (playerId == null) {
            return;
        }
        sessions.remove(playerId);
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    private record DpsSession(
        Instant startedAt,
        Instant lastHitAt,
        long totalDamage
    ) {
    }
}
