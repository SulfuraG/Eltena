package com.eltena.soundcore.live;

public record LiveAssetDispatchResult(
    String assetId,
    String displayName,
    int targetCount,
    int notReadyCount
) {
}
