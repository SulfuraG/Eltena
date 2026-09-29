package com.eltena.soundcore.api;

import com.eltena.soundcore.EltenaSoundCorePlugin;
import com.eltena.soundcore.sound.SoundCategory;
import org.bukkit.entity.Player;

public final class EltenaSoundApiService implements EltenaSoundApi {
    private final EltenaSoundCorePlugin plugin;

    public EltenaSoundApiService(EltenaSoundCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean hasSound(String soundId) {
        return soundId != null
            && !soundId.isBlank()
            && plugin.soundRegistry().soundIds().contains(soundId);
    }

    @Override
    public boolean playSound(Player player, String soundId) {
        return plugin.playbackService().play(player, soundId);
    }

    @Override
    public boolean hasLiveAsset(String assetId) {
        return assetId != null
            && !assetId.isBlank()
            && plugin.liveAssetRegistry().liveAssetIds().contains(assetId);
    }

    @Override
    public boolean playLiveAsset(Player player, String assetId) {
        try {
            return plugin.liveAssetPlaybackService().play(player, assetId).targetCount() > 0;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public long getSoundDurationMs(String soundId) {
        if (!hasSound(soundId)) {
            return 0L;
        }
        return plugin.soundRegistry().require(soundId).durationMs();
    }

    @Override
    public long getLiveAssetDurationMs(String assetId) {
        if (!hasLiveAsset(assetId)) {
            return 0L;
        }
        return plugin.liveAssetRegistry().require(assetId).durationMs();
    }

    @Override
    public boolean stopCategory(Player player, String category, long fadeOutMs) {
        if (player == null || category == null || category.isBlank()) {
            return false;
        }

        SoundCategory resolvedCategory;
        try {
            resolvedCategory = SoundCategory.fromConfig(category);
        } catch (IllegalArgumentException exception) {
            return false;
        }

        return plugin.playbackService().stop(player, resolvedCategory, fadeOutMs);
    }
}
