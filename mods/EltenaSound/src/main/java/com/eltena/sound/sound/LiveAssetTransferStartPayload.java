package com.eltena.sound.sound;

public record LiveAssetTransferStartPayload(
    String assetId,
    String assetType,
    String fileName,
    int version,
    String sha256,
    long sizeBytes,
    int chunkSize,
    int totalChunks,
    ClientSoundCategory category
) {
}
