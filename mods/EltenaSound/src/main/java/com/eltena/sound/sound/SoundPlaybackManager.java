package com.eltena.sound.sound;

import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

public final class SoundPlaybackManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEBUG_PROPERTY = "eltenasound.debug";
    private static final String LEGACY_DEBUG_PROPERTY = "eltena.debug";
    private static final ArrayDeque<PendingCommand> PENDING = new ArrayDeque<>();
    private static final List<ManagedSoundInstance> ACTIVE = new ArrayList<>();
    private static final LiveAssetManager LIVE_ASSETS = new LiveAssetManager();
    private static SoundConfigPayload soundConfig = SoundConfigPayload.defaults();
    private static ManagedSoundInstance activeBgm;
    private static boolean vanillaSuppressionActive;
    private static int vanillaSuppressTickCounter;

    private SoundPlaybackManager() {
    }

    public static void register(IEventBus eventBus) {
        eventBus.addListener(SoundPlaybackManager::onClientTick);
        eventBus.addListener(SoundPlaybackManager::onSelectMusic);
    }

    public static void enqueuePlay(PlaySoundPayload payload) {
        PENDING.addLast(new PendingCommand.Play(payload));
    }

    public static void enqueueLiveManifest(List<LiveAssetManifestEntry> assets) {
        PENDING.addLast(new PendingCommand.LiveManifest(List.copyOf(assets)));
    }

    public static void enqueueLivePlay(PlayLiveAssetPayload payload) {
        PENDING.addLast(new PendingCommand.LivePlay(payload));
    }

    public static void enqueueLiveTransferStart(LiveAssetTransferStartPayload payload) {
        PENDING.addLast(new PendingCommand.LiveTransferStart(payload));
    }

    public static void enqueueLiveTransferChunk(LiveAssetTransferChunkPayload payload) {
        PENDING.addLast(new PendingCommand.LiveTransferChunk(payload));
    }

    public static void enqueueLiveTransferComplete(LiveAssetTransferCompletePayload payload) {
        PENDING.addLast(new PendingCommand.LiveTransferComplete(payload));
    }

    public static void enqueueStop(StopSoundPayload payload) {
        PENDING.addLast(new PendingCommand.Stop(payload));
    }

    public static void enqueueConfig(SoundConfigPayload payload) {
        PENDING.addLast(new PendingCommand.Config(payload == null ? SoundConfigPayload.defaults() : payload));
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundManager() == null) {
            PENDING.clear();
            ACTIVE.clear();
            LIVE_ASSETS.reset();
            soundConfig = SoundConfigPayload.defaults();
            activeBgm = null;
            vanillaSuppressionActive = false;
            vanillaSuppressTickCounter = 0;
            return;
        }

        pruneInactive(minecraft);
        drainPending(minecraft);
        LIVE_ASSETS.tick(minecraft);
        maintainVanillaMusicControl(minecraft);
    }

    private static void drainPending(Minecraft minecraft) {
        while (!PENDING.isEmpty()) {
            PendingCommand command = PENDING.removeFirst();
            if (command instanceof PendingCommand.Play playCommand) {
                handlePlay(minecraft, playCommand.payload());
            } else if (command instanceof PendingCommand.LiveManifest manifestCommand) {
                LIVE_ASSETS.applyManifest(manifestCommand.assets());
            } else if (command instanceof PendingCommand.LivePlay livePlayCommand) {
                LIVE_ASSETS.queuePlay(minecraft, livePlayCommand.payload());
            } else if (command instanceof PendingCommand.LiveTransferStart startCommand) {
                LIVE_ASSETS.beginTransfer(startCommand.payload());
            } else if (command instanceof PendingCommand.LiveTransferChunk chunkCommand) {
                LIVE_ASSETS.acceptTransferChunk(chunkCommand.payload());
            } else if (command instanceof PendingCommand.LiveTransferComplete completeCommand) {
                LIVE_ASSETS.completeTransfer(minecraft, completeCommand.payload());
            } else if (command instanceof PendingCommand.Stop stopCommand) {
                handleStop(minecraft, stopCommand.payload());
            } else if (command instanceof PendingCommand.Config configCommand) {
                handleConfig(minecraft, configCommand.payload());
            }
        }
    }

    private static void handlePlay(Minecraft minecraft, PlaySoundPayload payload) {
        if (payload.soundEvent() == null || payload.soundEvent().isBlank()) {
            if (isDebugEnabled()) {
                LOGGER.warn(translate("log.eltenasound.unknown_sound_id", payload.soundId()));
                LOGGER.warn(translate("log.eltenasound.invalid_sound_event", payload.soundId()));
            }
            return;
        }

        ResourceLocation soundEventId;
        try {
            soundEventId = ResourceLocation.parse(payload.soundEvent());
        } catch (RuntimeException exception) {
            if (isDebugEnabled()) {
                LOGGER.warn(translate("log.eltenasound.unknown_sound_id", payload.soundId()));
                LOGGER.warn(translate("log.eltenasound.invalid_sound_event", payload.soundEvent()));
            }
            return;
        }

        if (isDebugEnabled()) {
            LOGGER.info(
                translate(
                    "log.eltenasound.play_sound",
                    payload.soundId(),
                    payload.category().name()
                )
            );
        }

        if (payload.category() == ClientSoundCategory.BGM) {
            handleBgmPlay(minecraft, payload, soundEventId);
            return;
        }

        ManagedSoundInstance instance = createInstance(payload, soundEventId);
        ACTIVE.add(instance);
        minecraft.getSoundManager().play(instance);
    }

    private static void handleStop(Minecraft minecraft, StopSoundPayload payload) {
        if (isDebugEnabled()) {
            LOGGER.info(translate("log.eltenasound.stop_sound", payload.category().name()));
        }

        if (payload.category() == ClientSoundCategory.BGM) {
            if (activeBgm == null) {
                return;
            }
            if (isDebugEnabled()) {
                LOGGER.info(
                    translate(
                        "log.eltenasound.bgm_fade_out_start",
                        activeBgm.soundId(),
                        Long.toString(payload.fadeOutMs())
                    )
                );
            }
            activeBgm.beginFadeOut(payload.fadeOutMs());
            activeBgm = null;
            if (shouldRestoreVanillaMusic()) {
                tryRestoreVanillaMusic(minecraft);
            }
            return;
        }

        if (payload.category() == ClientSoundCategory.VOICE) {
            LIVE_ASSETS.stopCategory(ClientSoundCategory.VOICE);
            return;
        }

        for (ManagedSoundInstance instance : ACTIVE) {
            if (instance.category() != payload.category() || instance.isCompleted()) {
                continue;
            }
            instance.beginFadeOut(payload.fadeOutMs());
        }
    }

    private static void handleConfig(Minecraft minecraft, SoundConfigPayload payload) {
        soundConfig = payload == null ? SoundConfigPayload.defaults() : payload.normalized();
        vanillaSuppressTickCounter = 0;
        if (isDebugEnabled()) {
            LOGGER.info(
                translate(
                    "log.eltenasound.config_applied",
                    Boolean.toString(soundConfig.vanillaMusicControl().enabled()),
                    soundConfig.vanillaMusicControl().mode().name(),
                    Integer.toString(soundConfig.vanillaMusicControl().intervalTicks())
                )
            );
        }
        if (shouldAlwaysSuppressVanillaMusic()) {
            stopVanillaMusic(minecraft, "config");
        } else if (soundConfig.vanillaMusicControl().enabled()
            && soundConfig.vanillaMusicControl().stopOnJoin()) {
            stopVanillaMusic(minecraft, "join");
        }
    }

    private static void handleBgmPlay(
        Minecraft minecraft,
        PlaySoundPayload payload,
        ResourceLocation soundEventId
    ) {
        if (shouldStopVanillaMusicOnPlay()) {
            stopVanillaMusic(minecraft, "play");
        }

        if (activeBgm != null
            && !activeBgm.isCompleted()
            && !activeBgm.isFadingOut()
            && activeBgm.soundId().equals(payload.soundId())) {
            activeBgm.refreshForReplay(
                payload.loop(),
                (float) payload.volume(),
                (float) payload.pitch(),
                payload.fadeInMs(),
                payload.fadeOutMs()
            );
            if (isDebugEnabled()) {
                LOGGER.info(
                    translate(
                        "log.eltenasound.bgm_fade_in_start",
                        payload.soundId(),
                        Long.toString(payload.fadeInMs())
                    )
                );
            }
            return;
        }

        ManagedSoundInstance previous = activeBgm;
        if (previous != null && !previous.isCompleted() && !previous.isFadingOut()) {
            long fadeOutMs = previous.defaultFadeOutMs();
            previous.beginFadeOut(fadeOutMs);
            if (isDebugEnabled()) {
                LOGGER.info(
                    translate(
                        "log.eltenasound.bgm_fade_out_start",
                        previous.soundId(),
                        Long.toString(fadeOutMs)
                    )
                );
            }
        }

        ManagedSoundInstance instance = createInstance(payload, soundEventId);
        activeBgm = instance;
        ACTIVE.add(instance);
        minecraft.getSoundManager().play(instance);

        if (isDebugEnabled()) {
            LOGGER.info(
                translate(
                    "log.eltenasound.bgm_fade_in_start",
                    payload.soundId(),
                    Long.toString(payload.fadeInMs())
                )
            );
            if (previous != null) {
                LOGGER.info(
                    translate(
                        "log.eltenasound.bgm_replaced",
                        previous.soundId(),
                        payload.soundId()
                    )
                );
            }
        }
    }

    private static void maintainVanillaMusicControl(Minecraft minecraft) {
        boolean shouldSuppress = shouldSuppressVanillaMusic();
        if (!shouldSuppress) {
            vanillaSuppressTickCounter = 0;
            if (vanillaSuppressionActive && shouldRestoreVanillaMusic()) {
                tryRestoreVanillaMusic(minecraft);
            }
            vanillaSuppressionActive = false;
            return;
        }

        vanillaSuppressionActive = true;
        vanillaSuppressTickCounter++;
        if (vanillaSuppressTickCounter >= soundConfig.vanillaMusicControl().intervalTicks()) {
            vanillaSuppressTickCounter = 0;
            stopVanillaMusic(minecraft, "interval");
        }
    }

    private static void onSelectMusic(SelectMusicEvent event) {
        if (event == null || !shouldSuppressVanillaMusic()) {
            return;
        }
        event.overrideMusic(null);
    }

    private static ManagedSoundInstance createInstance(
        PlaySoundPayload payload,
        ResourceLocation soundEventId
    ) {
        return new ManagedSoundInstance(
            payload.soundId(),
            payload.category(),
            soundEventId,
            payload.category().soundSource(),
            payload.loop(),
            (float) payload.volume(),
            (float) payload.pitch(),
            payload.fadeInMs(),
            payload.fadeOutMs()
        );
    }

    private static void pruneInactive(Minecraft minecraft) {
        Iterator<ManagedSoundInstance> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            ManagedSoundInstance instance = iterator.next();
            if (instance.isCompleted() || !minecraft.getSoundManager().isActive(instance)) {
                if (instance == activeBgm) {
                    activeBgm = null;
                }
                iterator.remove();
            }
        }
    }

    private static boolean shouldStopVanillaMusicOnPlay() {
        return shouldAlwaysSuppressVanillaMusic()
            || (soundConfig.vanillaMusicControl().enabled()
                && soundConfig.vanillaMusicControl().mode() == VanillaMusicControlMode.SUPPRESS_WHILE_ELTENA_BGM
                && soundConfig.vanillaMusicControl().stopOnPlay());
    }

    private static boolean shouldSuppressVanillaMusic() {
        VanillaMusicControlConfig control = soundConfig.vanillaMusicControl();
        if (!control.enabled()) {
            return false;
        }
        if (control.mode() == VanillaMusicControlMode.ALWAYS_SUPPRESS) {
            return true;
        }
        return control.mode() == VanillaMusicControlMode.SUPPRESS_WHILE_ELTENA_BGM
            && activeBgm != null
            && !activeBgm.isCompleted();
    }

    private static boolean shouldAlwaysSuppressVanillaMusic() {
        VanillaMusicControlConfig control = soundConfig.vanillaMusicControl();
        return control.enabled() && control.mode() == VanillaMusicControlMode.ALWAYS_SUPPRESS;
    }

    private static boolean shouldRestoreVanillaMusic() {
        VanillaMusicControlConfig control = soundConfig.vanillaMusicControl();
        return control.enabled()
            && control.mode() != VanillaMusicControlMode.ALWAYS_SUPPRESS
            && control.restoreWhenNoEltenaBgm();
    }

    private static void stopVanillaMusic(Minecraft minecraft, String reason) {
        MusicManager musicManager = minecraft == null ? null : minecraft.getMusicManager();
        if (musicManager == null) {
            return;
        }
        musicManager.stopPlaying();
        if (isDebugEnabled() && !"interval".equals(reason)) {
            LOGGER.info(translate("log.eltenasound.vanilla_music_stopped", reason));
        }
    }

    private static void tryRestoreVanillaMusic(Minecraft minecraft) {
        MusicManager musicManager = minecraft == null ? null : minecraft.getMusicManager();
        if (musicManager == null) {
            return;
        }
        Music music = minecraft.getSituationalMusic();
        if (music == null) {
            return;
        }
        musicManager.startPlaying(music);
        if (isDebugEnabled()) {
            LOGGER.info(translate("log.eltenasound.vanilla_music_restored"));
        }
    }

    private static boolean isDebugEnabled() {
        return Boolean.getBoolean(DEBUG_PROPERTY) || Boolean.getBoolean(LEGACY_DEBUG_PROPERTY);
    }

    private static String translate(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private sealed interface PendingCommand permits PendingCommand.Play, PendingCommand.LiveManifest, PendingCommand.LivePlay, PendingCommand.LiveTransferStart, PendingCommand.LiveTransferChunk, PendingCommand.LiveTransferComplete, PendingCommand.Stop, PendingCommand.Config {
        record Play(PlaySoundPayload payload) implements PendingCommand {
        }

        record LiveManifest(List<LiveAssetManifestEntry> assets) implements PendingCommand {
        }

        record LivePlay(PlayLiveAssetPayload payload) implements PendingCommand {
        }

        record LiveTransferStart(LiveAssetTransferStartPayload payload) implements PendingCommand {
        }

        record LiveTransferChunk(LiveAssetTransferChunkPayload payload) implements PendingCommand {
        }

        record LiveTransferComplete(LiveAssetTransferCompletePayload payload) implements PendingCommand {
        }

        record Stop(StopSoundPayload payload) implements PendingCommand {
        }

        record Config(SoundConfigPayload payload) implements PendingCommand {
        }
    }
}
