package com.eltena.sound.sound;

import net.minecraft.Util;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

final class ManagedSoundInstance extends AbstractTickableSoundInstance {
    private final String soundId;
    private final ClientSoundCategory category;
    private float targetVolume;
    private long defaultFadeOutMs;
    private long fadeStartedAtMs;
    private long fadeDurationMs;
    private float fadeStartVolume;
    private float fadeTargetVolume;
    private FadeMode fadeMode = FadeMode.NONE;
    private boolean completed;

    ManagedSoundInstance(
        String soundId,
        ClientSoundCategory category,
        ResourceLocation soundEventId,
        SoundSource soundSource,
        boolean loop,
        float volume,
        float pitch,
        long fadeInMs,
        long fadeOutMs
    ) {
        super(
            SoundEvent.createVariableRangeEvent(soundEventId),
            soundSource,
            RandomSource.create()
        );
        this.soundId = soundId;
        this.category = category;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.x = 0.0D;
        this.y = 0.0D;
        this.z = 0.0D;
        this.volume = 0.0F;
        refreshForReplay(loop, volume, pitch, fadeInMs, fadeOutMs);
    }

    @Override
    public void tick() {
        if (completed || fadeMode == FadeMode.NONE) {
            return;
        }

        if (fadeDurationMs <= 0L) {
            finishFadeImmediately();
            return;
        }

        long now = Util.getMillis();
        float progress = clamp((now - fadeStartedAtMs) / (float) fadeDurationMs);
        volume = lerp(fadeStartVolume, fadeTargetVolume, progress);
        if (progress >= 1.0F) {
            finishFadeImmediately();
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    void refreshForReplay(boolean loop, float volume, float pitch, long fadeInMs, long fadeOutMs) {
        if (completed) {
            return;
        }
        this.looping = loop;
        this.pitch = Math.max(0.01F, pitch);
        this.targetVolume = Math.max(0.0F, volume);
        this.defaultFadeOutMs = Math.max(0L, fadeOutMs);

        if (fadeInMs <= 0L) {
            this.volume = this.targetVolume;
            this.fadeMode = FadeMode.NONE;
            this.fadeDurationMs = 0L;
            this.fadeTargetVolume = this.targetVolume;
            return;
        }

        startFade(FadeMode.IN, this.targetVolume, fadeInMs);
    }

    void beginFadeOut(long fadeOutMs) {
        if (completed || fadeMode == FadeMode.OUT) {
            return;
        }
        if (fadeOutMs <= 0L) {
            volume = 0.0F;
            completed = true;
            stop();
            return;
        }
        startFade(FadeMode.OUT, 0.0F, fadeOutMs);
    }

    boolean isFadingOut() {
        return fadeMode == FadeMode.OUT;
    }

    boolean isCompleted() {
        return completed;
    }

    String soundId() {
        return soundId;
    }

    ClientSoundCategory category() {
        return category;
    }

    long defaultFadeOutMs() {
        return defaultFadeOutMs;
    }

    private void startFade(FadeMode mode, float target, long durationMs) {
        this.fadeMode = mode;
        this.fadeStartedAtMs = Util.getMillis();
        this.fadeDurationMs = Math.max(0L, durationMs);
        this.fadeStartVolume = this.volume;
        this.fadeTargetVolume = Math.max(0.0F, target);
        if (this.fadeDurationMs <= 0L) {
            finishFadeImmediately();
        }
    }

    private void finishFadeImmediately() {
        volume = fadeTargetVolume;
        if (fadeMode == FadeMode.OUT) {
            volume = 0.0F;
            completed = true;
            stop();
            return;
        }
        volume = targetVolume;
        fadeMode = FadeMode.NONE;
    }

    private float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private float lerp(float start, float end, float progress) {
        return start + ((end - start) * progress);
    }

    private enum FadeMode {
        NONE,
        IN,
        OUT
    }
}
