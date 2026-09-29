package com.eltena.sound.sound;

public record StopSoundPayload(
    ClientSoundCategory category,
    long fadeOutMs
) {
}
