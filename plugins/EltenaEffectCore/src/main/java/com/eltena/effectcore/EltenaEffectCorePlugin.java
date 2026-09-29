package com.eltena.effectcore;

import com.eltena.effectcore.command.EltenaEffectCommand;
import com.eltena.effectcore.config.AreaTriggerRegistry;
import com.eltena.effectcore.config.EffectRegistry;
import com.eltena.effectcore.config.EffectSequenceRegistry;
import com.eltena.effectcore.config.LanguageService;
import com.eltena.effectcore.config.RealtimeEventRegistry;
import com.eltena.effectcore.config.VirtualCameraConfigRegistry;
import com.eltena.effectcore.integration.EltenaSoundApiBridge;
import com.eltena.effectcore.playback.EffectPlaybackService;
import com.eltena.effectcore.playback.EffectSequencePlaybackService;
import com.eltena.effectcore.trigger.AreaTriggerService;
import com.eltena.effectcore.transport.EffectPluginMessenger;
import com.eltena.effectcore.transport.VirtualCameraSyncService;
import com.eltena.effectcore.virtualcamera.VirtualCameraConfiguration;
import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class EltenaEffectCorePlugin extends JavaPlugin {
    private final DecimalFormat decimalFormat = new DecimalFormat(
        "0.##",
        DecimalFormatSymbols.getInstance(Locale.ROOT)
    );

    private LanguageService languageService;
    private EffectRegistry effectRegistry;
    private EffectSequenceRegistry effectSequenceRegistry;
    private AreaTriggerRegistry areaTriggerRegistry;
    private RealtimeEventRegistry realtimeEventRegistry;
    private VirtualCameraConfigRegistry virtualCameraConfigRegistry;
    private EffectPluginMessenger messenger;
    private VirtualCameraSyncService virtualCameraSyncService;
    private EffectPlaybackService playbackService;
    private EffectSequencePlaybackService effectSequencePlaybackService;
    private AreaTriggerService areaTriggerService;
    private EltenaSoundApiBridge soundApiBridge;
    private EltenaEffectCommand effectCommandExecutor;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveBundledResource("language/ja_jp.yml");
        saveBundledResource("language/en_us.yml");
        saveBundledResource("effects/earthquake/tremor_small.yml");
        saveBundledResource("effects/earthquake/volcanic_warning.yml");
        saveBundledResource("effects/earthquake/meteor_shockwave.yml");
        saveBundledResource("effects/earthquake/jitter_test.yml");
        saveBundledResource("effects/earthquake/pulse_test.yml");
        saveBundledResource("effects/screen/dragon_roar_distortion.yml");
        saveBundledResource("effects/screen/meteor_shockwave_visual.yml");
        saveBundledResource("effects/screen/hit_stop_feel.yml");
        saveBundledResource("effects/screen/blood_pressure.yml");
        saveBundledResource("effects/screen/dark_pressure.yml");
        saveBundledResource("effects/screen/world_event_warning.yml");
        saveBundledResource("effects/screen/dimension_shift.yml");
        saveBundledResource("effects/screen/void_collapse.yml");
        saveBundledResource("effects/screen/heat_haze.yml");
        saveBundledResource("effects/screen/blizzard_whiteout.yml");
        saveBundledResource("effects/screen/underwater_pressure.yml");
        saveBundledResource("effects/screen/enrage_aura.yml");
        saveBundledResource("effects/screen/fear_wave.yml");
        saveBundledResource("effects/screen/time_distortion.yml");
        saveBundledResource("area-triggers/test/dark_pressure_area.yml");
        saveBundledResource("area-triggers/test/dragon_roar_area.yml");
        saveBundledResource("effect-sequences/meteor_impact_sequence.yml");
        saveBundledResource("effect-sequences/volcanic_activity.yml");
        saveBundledResource("effect-sequences/dragon_roar_showcase.yml");
        saveBundledResource("camera/camera.yml");
        saveBundledResource("camera/presets/exploration.yml");
        saveBundledResource("camera/presets/action_mmo.yml");
        saveBundledResource("camera/presets/fixed_showcase.yml");
        saveBundledResource("camera/presets/boss_showcase.yml");
        saveBundledResource("camera/presets/conversation.yml");
        saveBundledResource("camera/presets/life_action.yml");
        saveBundledResource("camera/presets/overhead.yml");
        saveBundledResource("camera/presets/debug_freecam.yml");
        ensureDataDirectory("realtime-events");

        this.languageService = new LanguageService(this);
        this.effectRegistry = new EffectRegistry(this);
        this.effectSequenceRegistry = new EffectSequenceRegistry(this);
        this.areaTriggerRegistry = new AreaTriggerRegistry(this, effectRegistry, effectSequenceRegistry);
        this.realtimeEventRegistry = new RealtimeEventRegistry(this);
        this.virtualCameraConfigRegistry = new VirtualCameraConfigRegistry(this);
        this.messenger = new EffectPluginMessenger(this);
        this.messenger.registerChannel();
        this.virtualCameraSyncService = new VirtualCameraSyncService(this, messenger);
        this.virtualCameraSyncService.register();
        this.soundApiBridge = new EltenaSoundApiBridge(this);

        reloadPluginState();
        registerCommand();
        logCommandRegistrationState();
        logInfo("log.enable", runtimeStatePlaceholders());
    }

    @Override
    public void onDisable() {
        if (areaTriggerService != null) {
            areaTriggerService.shutdown();
        }
        if (messenger != null) {
            messenger.unregisterChannel();
        }
        if (virtualCameraSyncService != null) {
            virtualCameraSyncService.unregister();
        }
        if (languageService != null) {
            getLogger().info(languageService.plain("log.disable", Map.of()));
        }
    }

    public void reloadPluginState() {
        reloadConfig();
        languageService.load();
        effectRegistry.load();
        effectSequenceRegistry.load();
        areaTriggerRegistry.load();
        realtimeEventRegistry.load();
        virtualCameraConfigRegistry.load();
        playbackService = new EffectPlaybackService(this, effectRegistry, messenger);
        effectSequencePlaybackService = new EffectSequencePlaybackService(
            this,
            effectSequenceRegistry,
            playbackService,
            soundApiBridge,
            virtualCameraSyncService
        );
        if (areaTriggerService != null) {
            areaTriggerService.shutdown();
        }
        areaTriggerService = new AreaTriggerService(
            this,
            areaTriggerRegistry,
            playbackService,
            effectSequencePlaybackService
        );
        areaTriggerService.start();
        int syncedPlayers = 0;
        if (virtualCameraSyncService != null) {
            syncedPlayers = virtualCameraSyncService.handleReloadSync();
        }
        logVirtualCameraReloadSummary(syncedPlayers);
    }

    public LanguageService language() {
        return languageService;
    }

    public EffectRegistry effectRegistry() {
        return effectRegistry;
    }

    public EffectSequenceRegistry effectSequenceRegistry() {
        return effectSequenceRegistry;
    }

    public AreaTriggerRegistry areaTriggerRegistry() {
        return areaTriggerRegistry;
    }

    public RealtimeEventRegistry realtimeEventRegistry() {
        return realtimeEventRegistry;
    }

    public VirtualCameraConfiguration virtualCameraConfig() {
        return virtualCameraConfigRegistry.configuration();
    }

    public VirtualCameraSyncService virtualCameraSyncService() {
        return virtualCameraSyncService;
    }

    public EffectPlaybackService playbackService() {
        return playbackService;
    }

    public EffectSequencePlaybackService sequencePlaybackService() {
        return effectSequencePlaybackService;
    }

    public AreaTriggerService areaTriggerService() {
        return areaTriggerService;
    }

    public boolean isDebugLogEnabled() {
        return getConfig().getBoolean("debug-log", false);
    }

    public void sendPrefixed(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(languageService.prefixed(key, placeholders));
    }

    public void sendPrefixedLiteral(CommandSender sender, String message) {
        sender.sendMessage(languageService.prefixedLiteral(message));
    }

    public void logInfo(String key, Map<String, String> placeholders) {
        getLogger().info(languageService.plain(key, placeholders));
    }

    public void logWarning(String key, Map<String, String> placeholders) {
        getLogger().warning(languageService.plain(key, placeholders));
    }

    public void log(Level level, String key, Map<String, String> placeholders, Throwable exception) {
        getLogger().log(level, languageService.plain(key, placeholders), exception);
    }

    public String formatDecimal(double value) {
        synchronized (decimalFormat) {
            return decimalFormat.format(value);
        }
    }

    public Map<String, String> runtimeStatePlaceholders() {
        LinkedHashMap<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("effects", Integer.toString(effectRegistry.count()));
        placeholders.put("sequences", Integer.toString(effectSequenceRegistry.count()));
        placeholders.put("triggers", Integer.toString(areaTriggerRegistry.count()));
        placeholders.put("realtimeEvents", Integer.toString(realtimeEventRegistry.count()));
        placeholders.put("locale", languageService.locale());
        return placeholders;
    }

    private void saveBundledResource(String path) {
        File destination = new File(getDataFolder(), path);
        if (!destination.exists()) {
            saveResource(path, false);
        }
    }

    private void ensureDataDirectory(String path) {
        File directory = new File(getDataFolder(), path);
        if (!directory.exists() && !directory.mkdirs()) {
            getLogger().warning("Failed to create data directory: " + directory.getAbsolutePath());
        }
    }

    private void registerCommand() {
        PluginCommand command = getCommand("eltenaeffect");
        if (command == null) {
            throw new IllegalStateException("Command eltenaeffect is missing from plugin.yml");
        }
        this.effectCommandExecutor = new EltenaEffectCommand(this);
        command.setExecutor(effectCommandExecutor);
        command.setTabCompleter(effectCommandExecutor);
    }

    private void logCommandRegistrationState() {
        PluginCommand command = getCommand("eltenaeffect");
        if (command == null) {
            return;
        }
        File loadedJar = getFile();
        getLogger().info(
            "[EltenaEffectCore] Command registration"
                + " jar=" + loadedJar.getAbsolutePath()
                + " lastModified=" + loadedJar.lastModified()
                + " command=" + command.getName()
                + " usage=" + command.getUsage()
                + " permission=" + command.getPermission()
        );
    }

    private void logVirtualCameraReloadSummary(int syncedPlayers) {
        VirtualCameraConfigRegistry.LoadSummary summary = virtualCameraConfigRegistry.lastLoadSummary();
        getLogger().info(
            "VirtualCamera camera.yml 読み込み結果: "
                + (summary.rootLoaded() ? "成功" : "既定値へフォールバック")
                + " file=" + summary.rootFilePath()
                + " enabled=" + virtualCameraConfig().enabled()
                + " configuredDefaultPreset=" + summary.configuredDefaultPreset()
                + " resolvedDefaultPreset=" + String.valueOf(summary.resolvedDefaultPreset())
        );
        getLogger().info(
            "VirtualCamera preset 読み込み結果: loaded="
                + summary.loadedPresets()
                + " invalid=" + summary.invalidPresets()
                + " duplicates=" + summary.duplicatePresets()
                + " disabled=" + summary.disabledPresets()
                + " presetFiles=" + summary.presetFiles()
                + " presetDirectory=" + summary.presetDirectoryPath()
                + " syncTargetPlayers=" + syncedPlayers
        );
    }
}
