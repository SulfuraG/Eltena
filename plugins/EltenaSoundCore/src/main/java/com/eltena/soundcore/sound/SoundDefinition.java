package com.eltena.soundcore.sound;

public record SoundDefinition(
    String id,
    SoundType type,
    String displayName,
    String soundEvent,
    SoundCategory category,
    boolean loop,
    double volume,
    double pitch,
    long durationMs,
    long fadeInMs,
    long fadeOutMs
) {
}
