package com.eltena.effectcore.effect;

public record EarthquakeEffectDefinition(
    String id,
    String displayName,
    EarthquakeShakePattern shakePattern,
    EffectScope scope,
    double radius,
    double basePower,
    double waveSpeed,
    long durationMs,
    long riseMs,
    long fallMs,
    double nearInstantRadius,
    boolean distanceDelay,
    DistanceFalloffDefinition distanceFalloff,
    RepeatSettings repeat
) implements EffectDefinition {
}
