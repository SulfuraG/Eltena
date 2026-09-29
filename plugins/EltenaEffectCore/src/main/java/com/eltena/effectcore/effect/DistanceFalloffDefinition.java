package com.eltena.effectcore.effect;

public record DistanceFalloffDefinition(
    DistanceFalloffType type,
    double minimumStrength
) {
}
