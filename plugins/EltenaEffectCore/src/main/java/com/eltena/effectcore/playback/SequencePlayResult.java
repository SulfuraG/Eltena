package com.eltena.effectcore.playback;

public record SequencePlayResult(
    String sequenceId,
    String displayName,
    int stepCount
) {
}
