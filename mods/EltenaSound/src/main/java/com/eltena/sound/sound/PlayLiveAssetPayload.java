package com.eltena.sound.sound;

public record PlayLiveAssetPayload(
    String assetId,
    String assetType,
    int version,
    String sha256,
    String fileName,
    ClientSoundCategory category,
    double volume,
    double pitch
) {
}
