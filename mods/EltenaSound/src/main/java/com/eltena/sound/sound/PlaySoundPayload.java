package com.eltena.sound.sound;

public record PlaySoundPayload(
    String soundId,
    String soundEvent,
    ClientSoundCategory category,
    boolean loop,
    double volume,
    double pitch,
    long fadeInMs,
    long fadeOutMs
) {
}
