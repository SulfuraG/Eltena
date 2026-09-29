package com.eltena.sound.sound;

public record LiveAssetManifestEntry(
    String id,
    String assetType,
    int version,
    String sha256,
    String fileName,
    long sizeBytes,
    ClientSoundCategory category
) {
}
