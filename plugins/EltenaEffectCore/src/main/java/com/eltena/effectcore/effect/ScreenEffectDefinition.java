package com.eltena.effectcore.effect;

public record ScreenEffectDefinition(
    String id,
    String displayName,
    ScreenVisualPattern visualPattern,
    long durationMs,
    double strength,
    long fadeInMs,
    long fadeOutMs,
    ScreenColorDefinition color,
    ScreenShaderDefinition shader
) implements EffectDefinition {
}
