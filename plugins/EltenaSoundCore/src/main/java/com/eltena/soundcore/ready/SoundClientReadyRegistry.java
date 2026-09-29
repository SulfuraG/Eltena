package com.eltena.soundcore.ready;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public final class SoundClientReadyRegistry {
    private final Map<UUID, SoundClientReadyState> readyPlayers = new ConcurrentHashMap<>();

    public void register(Player player, String protocolVersion, boolean supportsLiveAssets) {
        if (player == null) {
            return;
        }
        readyPlayers.put(player.getUniqueId(), new SoundClientReadyState(protocolVersion, supportsLiveAssets));
    }

    public SoundClientReadyState remove(UUID uniqueId) {
        if (uniqueId == null) {
            return null;
        }
        return readyPlayers.remove(uniqueId);
    }

    public void clear() {
        readyPlayers.clear();
    }

    public boolean isReady(Player player) {
        return player != null && readyPlayers.containsKey(player.getUniqueId());
    }

    public SoundClientReadyState state(Player player) {
        return player == null ? null : readyPlayers.get(player.getUniqueId());
    }

    public int count() {
        return readyPlayers.size();
    }

    public Collection<ReadyPlayerEntry> entriesByName() {
        ArrayList<ReadyPlayerEntry> entries = new ArrayList<>();
        for (Map.Entry<UUID, SoundClientReadyState> entry : readyPlayers.entrySet()) {
            entries.add(new ReadyPlayerEntry(entry.getKey(), entry.getValue()));
        }
        entries.sort(Comparator.comparing(ReadyPlayerEntry::uniqueId));
        return entries;
    }

    public record ReadyPlayerEntry(
        UUID uniqueId,
        SoundClientReadyState state
    ) {
    }
}
