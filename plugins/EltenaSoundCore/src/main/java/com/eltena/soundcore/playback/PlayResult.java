package com.eltena.soundcore.playback;

public record PlayResult(
    String soundId,
    String displayName,
    int targetCount,
    int notReadyCount
) {
}
