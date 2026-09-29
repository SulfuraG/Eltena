package com.eltena.core.application.player;

import com.eltena.core.bootstrap.ServiceRegistry;
import com.eltena.core.domain.player.PlayerOptionSettings;
import com.eltena.core.domain.player.PlayerProfile;
import com.eltena.core.domain.stats.PlayerStats;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PlayerResetService {

    private static final String[] MAX_HEALTH_ATTRIBUTE_NAMES = {
        "MAX_HEALTH",
        "GENERIC_MAX_HEALTH"
    };

    private final ServiceRegistry services;
    private Attribute maxHealthAttribute;
    private boolean maxHealthAttributeResolved;

    public PlayerResetService(ServiceRegistry services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    public PlayerResetResult reset(OfflinePlayer target) throws IOException {
        Objects.requireNonNull(target, "target");

        UUID playerId = target.getUniqueId();
        String playerName = safeName(target);
        Path playerDirectory = playerDirectory(playerId);
        List<String> deletedEntries = deleteStoredPlayerData(playerDirectory);

        clearRuntimeState(playerId);

        PlayerProfile profile = services.playerProfiles().loadOrCreate(playerId, playerName);
        PlayerStats stats = services.playerStats().loadOrCreate(playerId);
        PlayerOptionSettings settings = services.playerOptionSettingsService().resetToDefaults(playerId);

        boolean synced = false;
        if (target.isOnline() && target.getPlayer() != null) {
            Player player = target.getPlayer();
            services.playerDerivedStatsService().apply(player);
            restoreResetHealth(player, stats.hpExact());
            services.modSyncPluginMessenger().sendInitialSyncBundle(player);
            synced = true;
        }

        services.plugin().getLogger().info("[EltenaCore] resetplayer " + playerName + " completed");
        return new PlayerResetResult(playerId, playerName, List.copyOf(deletedEntries), profile, stats, settings, synced);
    }

    private void clearRuntimeState(UUID playerId) {
        services.playerProfiles().invalidate(playerId);
        services.playerStats().invalidate(playerId);
        services.playerOptionSettingsService().invalidate(playerId);
        services.abilitySystem().resetPlayerState(playerId);
        services.playerDamageFeedbackService().resetPlayerState(playerId);
        services.modSyncPluginMessenger().resetPlayerState(playerId);
    }

    private List<String> deleteStoredPlayerData(Path playerDirectory) throws IOException {
        List<String> deletedEntries = new ArrayList<>();
        if (playerDirectory == null || Files.notExists(playerDirectory)) {
            return deletedEntries;
        }

        try (var stream = Files.walk(playerDirectory)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.equals(playerDirectory) && Files.isRegularFile(path)) {
                    deletedEntries.add(playerDirectory.relativize(path).toString().replace('\\', '/'));
                }
                Files.deleteIfExists(path);
            }
        }
        deletedEntries.sort(String::compareTo);
        return deletedEntries;
    }

    private void restoreResetHealth(Player player, double baseHpExact) {
        if (player == null || !player.isOnline()) {
            return;
        }
        double nextHealth = Math.max(1.0D, baseHpExact);
        Attribute maxHealth = resolveMaxHealthAttribute();
        if (maxHealth != null) {
            AttributeInstance attribute = player.getAttribute(maxHealth);
            if (attribute != null) {
                nextHealth = Math.min(nextHealth, Math.max(1.0D, attribute.getValue()));
            }
        }
        player.setHealth(nextHealth);
    }

    private Attribute resolveMaxHealthAttribute() {
        if (maxHealthAttributeResolved) {
            return maxHealthAttribute;
        }
        maxHealthAttributeResolved = true;
        for (String candidate : MAX_HEALTH_ATTRIBUTE_NAMES) {
            try {
                Field field = Attribute.class.getField(candidate);
                Object value = field.get(null);
                if (value instanceof Attribute attribute) {
                    maxHealthAttribute = attribute;
                    break;
                }
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                // Try the next compatible name for the active runtime.
            }
        }
        return maxHealthAttribute;
    }

    private Path playerDirectory(UUID playerId) {
        return services.plugin().getDataFolder().toPath()
            .resolve("players")
            .resolve(playerId.toString());
    }

    private String safeName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    public record PlayerResetResult(
        UUID playerId,
        String playerName,
        List<String> deletedEntries,
        PlayerProfile profile,
        PlayerStats stats,
        PlayerOptionSettings settings,
        boolean synced
    ) {
    }
}
