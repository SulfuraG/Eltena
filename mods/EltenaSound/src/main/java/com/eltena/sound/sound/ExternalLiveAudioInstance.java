package com.eltena.sound.sound;

import com.mojang.blaze3d.audio.Library;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;

final class ExternalLiveAudioInstance {
    private static volatile Field soundEngineField;
    private static volatile Field channelAccessField;

    private final String assetId;
    private final ClientSoundCategory category;
    private final Path filePath;
    private final float volume;
    private final float pitch;
    private CompletableFuture<ChannelAccess.ChannelHandle> handleFuture;
    private ChannelAccess.ChannelHandle handle;
    private CompletableFuture<Boolean> stateFuture;
    private AudioStream stream;
    private boolean started;
    private boolean released;

    private ExternalLiveAudioInstance(
        String assetId,
        ClientSoundCategory category,
        Path filePath,
        float volume,
        float pitch
    ) {
        this.assetId = assetId;
        this.category = category;
        this.filePath = filePath;
        this.volume = volume;
        this.pitch = pitch;
    }

    static ExternalLiveAudioInstance start(
        Minecraft minecraft,
        String assetId,
        ClientSoundCategory category,
        Path filePath,
        float volume,
        float pitch
    ) {
        ChannelAccess channelAccess = resolveChannelAccess(minecraft);
        if (channelAccess == null) {
            return null;
        }

        ExternalLiveAudioInstance instance = new ExternalLiveAudioInstance(
            assetId,
            category,
            filePath,
            volume,
            pitch
        );
        instance.open(channelAccess);
        return instance;
    }

    String assetId() {
        return assetId;
    }

    ClientSoundCategory category() {
        return category;
    }

    boolean tick() {
        if (released) {
            return true;
        }
        if (!started || handle == null) {
            return false;
        }
        if (stateFuture == null) {
            stateFuture = new CompletableFuture<>();
            handle.execute(channel -> stateFuture.complete(channel.stopped()));
            return false;
        }
        if (!stateFuture.isDone()) {
            return false;
        }

        boolean stopped = Boolean.TRUE.equals(stateFuture.getNow(Boolean.FALSE));
        stateFuture = null;
        if (stopped) {
            release();
            return true;
        }
        return false;
    }

    void stopNow() {
        release();
    }

    private void open(ChannelAccess channelAccess) {
        handleFuture = channelAccess.createHandle(Library.Pool.STREAMING);
        handleFuture.whenComplete((createdHandle, throwable) -> {
            if (throwable != null || createdHandle == null) {
                release();
                return;
            }
            if (released) {
                createdHandle.release();
                return;
            }
            handle = createdHandle;
            try {
                InputStream inputStream = new BufferedInputStream(Files.newInputStream(filePath));
                stream = new JOrbisAudioStream(inputStream);
                createdHandle.execute(channel -> {
                    channel.setRelative(true);
                    channel.disableAttenuation();
                    channel.setLooping(false);
                    channel.setVolume(volume);
                    channel.setPitch(pitch);
                    channel.attachBufferStream(stream);
                    channel.play();
                });
                started = true;
            } catch (IOException exception) {
                release();
            }
        });
    }

    private void release() {
        if (released) {
            return;
        }
        released = true;
        if (handle != null) {
            handle.execute(com.mojang.blaze3d.audio.Channel::stop);
            handle.release();
            handle = null;
        }
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
            stream = null;
        }
    }

    private static ChannelAccess resolveChannelAccess(Minecraft minecraft) {
        if (minecraft == null) {
            return null;
        }
        SoundManager soundManager = minecraft.getSoundManager();
        if (soundManager == null) {
            return null;
        }
        try {
            Field soundEngine = soundEngineField;
            if (soundEngine == null) {
                soundEngine = findFieldByType(SoundManager.class, SoundEngine.class);
                soundEngineField = soundEngine;
            }
            Object engine = soundEngine.get(soundManager);
            if (!(engine instanceof SoundEngine resolvedEngine)) {
                return null;
            }

            Field channelAccess = channelAccessField;
            if (channelAccess == null) {
                channelAccess = findFieldByType(SoundEngine.class, ChannelAccess.class);
                channelAccessField = channelAccess;
            }
            Object resolvedChannelAccess = channelAccess.get(resolvedEngine);
            return resolvedChannelAccess instanceof ChannelAccess access ? access : null;
        } catch (IllegalAccessException | RuntimeException exception) {
            return null;
        }
    }

    private static Field findFieldByType(Class<?> owner, Class<?> fieldType) {
        for (Field field : owner.getDeclaredFields()) {
            if (!fieldType.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            return field;
        }
        throw new IllegalStateException("Failed to resolve field type " + fieldType.getName() + " in " + owner.getName());
    }
}
