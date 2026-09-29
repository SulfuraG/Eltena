package com.eltena.soundcore.playback;

import com.eltena.soundcore.sound.SoundCategory;

public record StopResult(
    SoundCategory category,
    int targetCount,
    long fadeOutMs,
    int notReadyCount
) {
}
