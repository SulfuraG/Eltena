package com.eltena.effect.effect;

public record EarthquakePayload(
    String effectId,
    EarthquakeShakePattern shakePattern,
    Source source,
    String scope,
    double radius,
    double basePower,
    double waveSpeed,
    long durationMs,
    long riseMs,
    long fallMs,
    double nearInstantRadius,
    boolean distanceDelay,
    DistanceFalloff distanceFalloff
) {
    public record Source(
        String world,
        double x,
        double y,
        double z
    ) {
    }

    public record DistanceFalloff(
        String type,
        double minimumStrength
    ) {
    }
}
