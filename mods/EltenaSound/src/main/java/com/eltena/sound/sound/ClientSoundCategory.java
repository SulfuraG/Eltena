package com.eltena.sound.sound;

import java.util.Locale;
import net.minecraft.sounds.SoundSource;

public enum ClientSoundCategory {
    BGM(SoundSource.MUSIC),
    SYSTEM(SoundSource.MASTER),
    VOICE(SoundSource.VOICE);

    private final SoundSource soundSource;

    ClientSoundCategory(SoundSource soundSource) {
        this.soundSource = soundSource;
    }

    public SoundSource soundSource() {
        return soundSource;
    }

    public static ClientSoundCategory fromPayload(String value) {
        ClientSoundCategory category = tryFromPayload(value);
        return category == null ? SYSTEM : category;
    }

    public static ClientSoundCategory tryFromPayload(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "BGM" -> BGM;
            case "SYSTEM" -> SYSTEM;
            case "VOICE" -> VOICE;
            default -> null;
        };
    }
}
