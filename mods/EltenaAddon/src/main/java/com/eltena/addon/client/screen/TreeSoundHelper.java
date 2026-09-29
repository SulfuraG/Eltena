package com.eltena.addon.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

final class TreeSoundHelper {
    private static final float QUIET_VOLUME = 0.65F;

    private TreeSoundHelper() {
    }

    static void playSelect() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), QUIET_VOLUME, 1.0F);
    }

    static void playSuccess() {
        play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.55F, 1.05F);
    }

    static void playFailure() {
        play(SoundEvents.VILLAGER_NO, 0.45F, 1.15F);
    }

    private static void play(SoundEvent sound, float volume, float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundManager() == null) {
            return;
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
