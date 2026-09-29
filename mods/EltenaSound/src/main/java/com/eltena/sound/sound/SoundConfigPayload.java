package com.eltena.sound.sound;

public record SoundConfigPayload(
    VanillaMusicControlConfig vanillaMusicControl
) {
    public static SoundConfigPayload defaults() {
        return new SoundConfigPayload(VanillaMusicControlConfig.defaults());
    }

    public SoundConfigPayload normalized() {
        VanillaMusicControlConfig config = vanillaMusicControl == null
            ? VanillaMusicControlConfig.defaults()
            : vanillaMusicControl.normalized();
        return new SoundConfigPayload(config);
    }
}
