package com.eltena.soundcore.playback;

import com.eltena.soundcore.EltenaSoundCorePlugin;
import com.eltena.soundcore.config.SoundRegistry;
import com.eltena.soundcore.config.VanillaMusicControlSettings;
import com.eltena.soundcore.sound.SoundCategory;
import com.eltena.soundcore.sound.SoundDefinition;
import com.eltena.soundcore.transport.SoundPluginMessenger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;

public final class SoundPlaybackService {
    public static final long DEFAULT_STOP_FADE_OUT_MS = 1000L;

    private final EltenaSoundCorePlugin plugin;
    private final SoundRegistry soundRegistry;
    private final SoundPluginMessenger messenger;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public SoundPlaybackService(
        EltenaSoundCorePlugin plugin,
        SoundRegistry soundRegistry,
        SoundPluginMessenger messenger
    ) {
        this.plugin = plugin;
        this.soundRegistry = soundRegistry;
        this.messenger = messenger;
    }

    public PlayResult play(String soundId) {
        SoundDefinition sound = soundRegistry.require(soundId);
        DispatchTargets targets = selectTargets();
        dispatchPlay(sound, targets);
        return new PlayResult(sound.id(), sound.displayName(), targets.readyPlayers().size(), targets.notReadyCount());
    }

    public boolean play(Player player, String soundId) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        SoundDefinition sound;
        try {
            sound = soundRegistry.require(soundId);
        } catch (IllegalArgumentException exception) {
            return false;
        }

        if (!plugin.isPlayerReady(player)) {
            logSkippedNotReady(player, "play_sound", soundId);
            return false;
        }

        dispatchPlay(sound, new DispatchTargets(List.of(player), 0));
        return true;
    }

    public void syncSoundConfig() {
        for (Player player : selectTargets().readyPlayers()) {
            syncSoundConfig(player);
        }
    }

    public boolean syncSoundConfig(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (!plugin.isPlayerReady(player)) {
            logSkippedNotReady(player, "sound_config", "");
            return false;
        }
        VanillaMusicControlSettings settings = plugin.vanillaMusicControlSettings();
        byte[] bytes = gson.toJson(buildSoundConfigPayload(settings)).getBytes(StandardCharsets.UTF_8);
        messenger.sendConfig(player, settings, bytes);
        return true;
    }

    public StopResult stop(SoundCategory category) {
        return stop(category, DEFAULT_STOP_FADE_OUT_MS);
    }

    public StopResult stop(SoundCategory category, long fadeOutMs) {
        long resolvedFadeOutMs = Math.max(0L, fadeOutMs);
        DispatchTargets targets = selectTargets();
        dispatchStop(category, resolvedFadeOutMs, targets);
        return new StopResult(category, targets.readyPlayers().size(), resolvedFadeOutMs, targets.notReadyCount());
    }

    public boolean stop(Player player, SoundCategory category, long fadeOutMs) {
        if (player == null || !player.isOnline() || category == null) {
            return false;
        }
        if (!plugin.isPlayerReady(player)) {
            logSkippedNotReady(player, "stop_sound", category.name());
            return false;
        }

        dispatchStop(category, Math.max(0L, fadeOutMs), new DispatchTargets(List.of(player), 0));
        return true;
    }

    private DispatchTargets selectTargets() {
        List<Player> readyPlayers = new ArrayList<>();
        int notReadyCount = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (plugin.isPlayerReady(player)) {
                readyPlayers.add(player);
            } else {
                notReadyCount++;
            }
        }
        return new DispatchTargets(readyPlayers, notReadyCount);
    }

    private void dispatchPlay(SoundDefinition sound, DispatchTargets targets) {
        byte[] bytes = gson.toJson(buildPlayPayload(sound)).getBytes(StandardCharsets.UTF_8);
        for (Player player : targets.readyPlayers()) {
            messenger.sendPlay(player, sound, bytes);
        }
        if (plugin.isDebugLogEnabled()) {
            plugin.logInfo(
                "log.play-play-sound",
                Map.of(
                    "soundId", sound.id(),
                    "category", sound.category().name(),
                    "targetCount", Integer.toString(targets.readyPlayers().size()),
                    "fadeInMs", Long.toString(sound.fadeInMs()),
                    "fadeOutMs", Long.toString(sound.fadeOutMs())
                )
            );
        }
        if (targets.readyPlayers().isEmpty() && plugin.isDebugLogEnabled()) {
            plugin.logInfo(
                "log.no-targets-play",
                Map.of("soundId", sound.id())
            );
        }
    }

    private void dispatchStop(SoundCategory category, long fadeOutMs, DispatchTargets targets) {
        byte[] bytes = gson.toJson(buildStopPayload(category, fadeOutMs))
            .getBytes(StandardCharsets.UTF_8);
        for (Player player : targets.readyPlayers()) {
            messenger.sendStop(player, category, fadeOutMs, bytes);
        }
        if (plugin.isDebugLogEnabled()) {
            plugin.logInfo(
                "log.play-stop-sound",
                Map.of(
                    "category", category.name(),
                    "targetCount", Integer.toString(targets.readyPlayers().size()),
                    "fadeOutMs", Long.toString(fadeOutMs)
                )
            );
        }
        if (targets.readyPlayers().isEmpty() && plugin.isDebugLogEnabled()) {
            plugin.logInfo(
                "log.no-targets-stop",
                Map.of("category", category.name())
            );
        }
    }

    private void logSkippedNotReady(Player player, String payloadType, String identifier) {
        if (!plugin.isDebugLogEnabled() || player == null) {
            return;
        }
        plugin.logInfo(
            "log.payload-skipped-not-ready",
            Map.of(
                "player", player.getName(),
                "payloadType", payloadType,
                "assetId", identifier == null || identifier.isBlank() ? "-" : identifier
            )
        );
    }

    private JsonObject buildPlayPayload(SoundDefinition sound) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "play_sound");
        payload.addProperty("soundId", sound.id());
        payload.addProperty("soundEvent", sound.soundEvent());
        payload.addProperty("category", sound.category().name());
        payload.addProperty("loop", sound.loop());
        payload.addProperty("volume", sound.volume());
        payload.addProperty("pitch", sound.pitch());
        payload.addProperty("fadeInMs", sound.fadeInMs());
        payload.addProperty("fadeOutMs", sound.fadeOutMs());
        return payload;
    }

    private JsonObject buildStopPayload(SoundCategory category, long fadeOutMs) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "stop_sound");
        payload.addProperty("category", category.name());
        payload.addProperty("fadeOutMs", fadeOutMs);
        return payload;
    }

    private JsonObject buildSoundConfigPayload(VanillaMusicControlSettings settings) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "sound_config");

        JsonObject vanillaMusicControl = new JsonObject();
        vanillaMusicControl.addProperty("enabled", settings.enabled());
        vanillaMusicControl.addProperty("mode", settings.mode());
        vanillaMusicControl.addProperty("stopOnJoin", settings.stopOnJoin());
        vanillaMusicControl.addProperty("intervalTicks", settings.intervalTicks());
        if (!VanillaMusicControlSettings.MODE_ALWAYS_SUPPRESS.equals(settings.mode())) {
            vanillaMusicControl.addProperty("stopOnPlay", settings.stopOnPlay());
            vanillaMusicControl.addProperty("restoreWhenNoEltenaBgm", settings.restoreWhenNoEltenaBgm());
        }
        payload.add("vanillaMusicControl", vanillaMusicControl);
        return payload;
    }

    private record DispatchTargets(
        List<Player> readyPlayers,
        int notReadyCount
    ) {
    }
}
