package com.eltena.soundcore.api;

import org.bukkit.entity.Player;

public interface EltenaSoundApi {
    boolean hasSound(String soundId);

    boolean playSound(Player player, String soundId);

    boolean hasLiveAsset(String assetId);

    boolean playLiveAsset(Player player, String assetId);

    long getSoundDurationMs(String soundId);

    long getLiveAssetDurationMs(String assetId);

    boolean stopCategory(Player player, String category, long fadeOutMs);
}
