package com.eltena.sound.sound;

public record LiveAssetTransferChunkPayload(
    String assetId,
    int index,
    String dataBase64
) {
}
