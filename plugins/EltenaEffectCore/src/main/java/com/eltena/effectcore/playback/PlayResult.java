package com.eltena.effectcore.playback;

public record PlayResult(
    String effectId,
    String displayName,
    int targetCount,
    int scheduledRepeatCount
) {
}
