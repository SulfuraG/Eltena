package com.eltena.soundcore.live;

import com.eltena.soundcore.sound.SoundCategory;
import java.nio.file.Path;

public record LiveAssetDefinition(
    String id,
    LiveAssetType type,
    String displayName,
    String file,
    String fileName,
    int version,
    String sha256,
    SoundCategory category,
    double volume,
    double pitch,
    long durationMs,
    long sizeBytes,
    Path absoluteFilePath
) {
}
